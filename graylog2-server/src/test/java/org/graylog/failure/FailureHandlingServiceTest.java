/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.failure;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.graylog.testing.messages.MessagesExtension;
import org.graylog2.Configuration;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.MessageFactory;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.journal.RawMessage;
import org.graylog2.shared.messageq.Acknowledgeable;
import org.graylog2.shared.messageq.MessageQueueAcknowledger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MessagesExtension.class)
public class FailureHandlingServiceTest {

    private final Configuration configuration = mock(Configuration.class);
    private final MessageQueueAcknowledger acknowledger = mock(MessageQueueAcknowledger.class);
    private final MetricRegistry metricRegistry = new MetricRegistry();
    private MessageFactory messageFactory;

    private FailureSubmissionQueue failureSubmissionQueue;

    @BeforeEach
    public void setup(MessageFactory messageFactory) {
        when(configuration.getFailureHandlingQueueCapacity()).thenReturn(1000);

        failureSubmissionQueue = new FailureSubmissionQueue(configuration, metricRegistry);
        this.messageFactory = messageFactory;
    }

    @Test
    public void run_whenNoSuitableCustomHandlerProvided_thenSuitableFallbackOneIsUsed() throws Exception {
        // given
        final FailureBatch indexingFailureBatch = indexingFailureBatch(createIndexingFailure());

        final FailureHandler customFailureHandler = enabledFailureHandler();
        final FailureHandler fallbackIndexingFailureHandler = enabledFailureHandler(indexingFailureBatch);

        final FailureHandlingService underTest = new FailureHandlingService(fallbackIndexingFailureHandler,
                Set.of(customFailureHandler), failureSubmissionQueue, configuration, acknowledger);

        underTest.startAsync();
        underTest.awaitRunning();

        //when
        failureSubmissionQueue.submitBlocking(indexingFailureBatch);

        Awaitility.waitAtMost(Durations.ONE_SECOND)
                .until(() -> failureSubmissionQueue.queueSize() == 0);

        //then
        verify(customFailureHandler, times(0)).handle(any());
        verify(fallbackIndexingFailureHandler).handle(indexingFailureBatch);
    }

    private static FailureHandler enabledFailureHandler(FailureBatch... supportedBatch) {
        final FailureHandler failureHandler = mock(FailureHandler.class);
        when(failureHandler.isEnabled()).thenReturn(true);
        Stream.of(supportedBatch).forEach(sb -> when(failureHandler.supports(sb)).thenReturn(true));
        return failureHandler;
    }

    @Test
    public void run_whenNoSuitableCustomHandlerAndNoSuitableFallbackHandlerFound_thenNoHandlingDone() throws Exception {
        // given
        final FailureBatch indexingFailureBatch = indexingFailureBatch(createIndexingFailure());

        final FailureHandler customFailureHandler = enabledFailureHandler();
        final FailureHandler fallbackFailureHandler = enabledFailureHandler();

        final FailureHandlingService underTest = new FailureHandlingService(fallbackFailureHandler,
                ImmutableSet.of(customFailureHandler), failureSubmissionQueue, configuration, acknowledger);

        underTest.startAsync();
        underTest.awaitRunning();

        //when
        failureSubmissionQueue.submitBlocking(indexingFailureBatch);

        Awaitility.waitAtMost(Durations.ONE_SECOND)
                .until(() -> failureSubmissionQueue.queueSize() == 0);

        //then
        verify(customFailureHandler, times(0)).handle(any());
        verify(fallbackFailureHandler, times(0)).handle(any());
    }

    private static FailureHandler enabledFailureHandler() {
        final FailureHandler failureHandler = mock(FailureHandler.class);
        when(failureHandler.isEnabled()).thenReturn(true);
        when(failureHandler.supports(any())).thenReturn(false);
        return failureHandler;
    }

    @Test
    public void run_whenCustomHandlersProvided_thenFallbackHandlerIgnored() throws Exception {
        // given
        final FailureBatch indexingFailureBatch = indexingFailureBatch(createIndexingFailure());

        final FailureHandler customIndexingFailureHandler1 = enabledFailureHandler(indexingFailureBatch);
        final FailureHandler customIndexingFailureHandler2 = enabledFailureHandler(indexingFailureBatch);
        final FailureHandler fallbackIndexingFailureHandler = enabledFailureHandler(indexingFailureBatch);

        final FailureHandlingService underTest = new FailureHandlingService(fallbackIndexingFailureHandler,
                ImmutableSet.of(customIndexingFailureHandler1, customIndexingFailureHandler2), failureSubmissionQueue, configuration, acknowledger);

        underTest.startAsync();
        underTest.awaitRunning();

        // when
        failureSubmissionQueue.submitBlocking(indexingFailureBatch);

        Awaitility.waitAtMost(Durations.ONE_SECOND)
                .until(() -> failureSubmissionQueue.queueSize() == 0);

        // then
        verify(customIndexingFailureHandler1).handle(indexingFailureBatch);
        verify(customIndexingFailureHandler2).handle(indexingFailureBatch);
        verify(fallbackIndexingFailureHandler, times(0)).handle(any());
    }

    @Test
    public void run_serviceNotInterruptedUponHandlerException() throws Exception {
        // given
        final FailureBatch indexingFailureBatch1 = indexingFailureBatch(createIndexingFailure());
        final FailureBatch indexingFailureBatch2 = indexingFailureBatch(createIndexingFailure());

        final FailureHandler fallbackIndexingFailureHandler = enabledFailureHandler(indexingFailureBatch1, indexingFailureBatch2);


        doThrow(new RuntimeException()).when(fallbackIndexingFailureHandler).handle(indexingFailureBatch2);

        final FailureHandlingService underTest = new FailureHandlingService(fallbackIndexingFailureHandler,
                ImmutableSet.of(), failureSubmissionQueue, configuration, acknowledger);

        underTest.startAsync();
        underTest.awaitRunning();

        // when
        failureSubmissionQueue.submitBlocking(indexingFailureBatch2);
        failureSubmissionQueue.submitBlocking(indexingFailureBatch1);

        Awaitility.waitAtMost(Durations.ONE_SECOND)
                .until(() -> failureSubmissionQueue.queueSize() == 0);

        // then
        verify(fallbackIndexingFailureHandler).handle(indexingFailureBatch2);
        verify(fallbackIndexingFailureHandler).handle(indexingFailureBatch1);
    }

    @Test
    public void run_acknowledgesFlaggedProcessingErrorsOnlyOnce() throws InterruptedException {
        // given
        final ProcessingFailure processingFailureWithAck = createProcessingFailure(true);
        final ProcessingFailure processingFailureNoAck = createProcessingFailure(false);
        final FailureBatch processingFailureBatch = processingFailureBatch(processingFailureWithAck, processingFailureNoAck);

        final FailureHandler fallbackFailureHandler = enabledFailureHandler();
        final FailureHandler customFailureHandler1 = enabledFailureHandler(processingFailureBatch);
        final FailureHandler customFailureHandler2 = enabledFailureHandler(processingFailureBatch);

        final FailureHandlingService underTest = new FailureHandlingService(fallbackFailureHandler,
                ImmutableSet.of(customFailureHandler1, customFailureHandler2),
                failureSubmissionQueue, configuration, acknowledger);

        // when
        underTest.startAsync();
        underTest.awaitRunning();

        failureSubmissionQueue.submitBlocking(processingFailureBatch);

        Awaitility.waitAtMost(Durations.ONE_SECOND)
                .until(() -> failureSubmissionQueue.queueSize() == 0);

        // then
        verify(acknowledger, times(1)).acknowledge(argThat((List<? extends Acknowledgeable> arg) ->
                arg.size() == 1 && arg.get(0) == processingFailureWithAck));
    }

    @Test
    public void run_doesNotAcknowledgeIndexingAndInputErrors() throws InterruptedException {
        // given
        final FailureHandler fallbackFailureHandler = enabledFailureHandler();

        final FailureBatch indexingFailureBatch = indexingFailureBatch(createIndexingFailure());
        final FailureHandler indexingFailureHandler = enabledFailureHandler(indexingFailureBatch);
        final FailureBatch inputFailureBatch = FailureBatch.inputFailureBatch(List.of(createInputFailure()));
        final FailureHandler inputFailureHandler = enabledFailureHandler(inputFailureBatch);

        final FailureHandlingService underTest = new FailureHandlingService(fallbackFailureHandler,
                ImmutableSet.of(indexingFailureHandler, inputFailureHandler), failureSubmissionQueue, configuration, acknowledger);

        // when
        underTest.startAsync();
        underTest.awaitRunning();

        failureSubmissionQueue.submitBlocking(indexingFailureBatch);
        failureSubmissionQueue.submitBlocking(inputFailureBatch);

        Awaitility.waitAtMost(Durations.ONE_SECOND)
                .until(() -> failureSubmissionQueue.queueSize() == 0);

        // then
        verifyNoInteractions(acknowledger);
    }

    @Test
    public void shutDown_uponShutdownAllRemainingFailuresAreHandled() throws Exception {
        // given
        final FailureBatch indexingFailureBatch = indexingFailureBatch(createIndexingFailure());

        final FailureHandler fallbackFailureHandler = enabledFailureHandler(indexingFailureBatch);

        final FailureSubmissionQueue failureSubmissionQueue = mock(FailureSubmissionQueue.class);
        final FailureHandlingService underTest = new FailureHandlingService(fallbackFailureHandler,
                ImmutableSet.of(), failureSubmissionQueue, configuration, acknowledger);

        when(configuration.getFailureHandlingShutdownAwait()).thenReturn(com.github.joschi.jadconfig.util.Duration.milliseconds(300));
        when(failureSubmissionQueue.consumeBlockingWithTimeout(300L))
                .thenReturn(indexingFailureBatch)
                .thenReturn(null);

        underTest.startAsync();
        underTest.awaitRunning();

        //when
        underTest.stopAsync();
        underTest.awaitTerminated();

        // then
        verify(failureSubmissionQueue, times(2)).consumeBlockingWithTimeout(300L);
        verify(fallbackFailureHandler, times(1)).handle(indexingFailureBatch);
    }

    private IndexingFailure createIndexingFailure() {
        return new IndexingFailure(
                IndexingFailureCause.MappingError, "Failure Message" + UUID.randomUUID(), "Failure Details",
                Tools.nowUTC(), null, "target-index"
        );
    }

    private ProcessingFailure createProcessingFailure(boolean ack) {
        Message message = messageFactory.createMessage(ImmutableMap.of("_id", "1234"));
        message.setMessageQueueId("message-queue-id");
        return new ProcessingFailure(
                ProcessingFailureCause.UNKNOWN, "Failure Message", "Failure Details",
                Tools.nowUTC(), message, ack);
    }

    private InputFailure createInputFailure() {
        return new InputFailure(InputFailureCause.INPUT_PARSE, "Failure Message", "Failure Details",
                Tools.nowUTC(), new RawMessage(new byte[]{}), "pyload");
    }

    private FailureBatch indexingFailureBatch(IndexingFailure indexingFailure) {
        return FailureBatch.indexingFailureBatch(List.of(indexingFailure));
    }

    private FailureBatch processingFailureBatch(ProcessingFailure... processingFailure) {
        return FailureBatch.processingFailureBatch(Arrays.asList(processingFailure));
    }
}
