import Immutable from 'immutable';

export default class Constraint {
  constructor(type, version, plugin = 'server') {
    this._value = { type, plugin, version };
  }

  get type() {
    return this._value.type;
  }

  get plugin() {
    return this._value.plugin;
  }

  get version() {
    return this._value.version;
  }

  toBuilder() {
    const { type, plugin, version } = this._value;
    // eslint-disable-next-line no-use-before-define
    return new Builder(Immutable.Map({ type, plugin, version }));
  }

  static create(type, version, plugin = 'server') {
    return new Constraint(type, version, plugin);
  }

  toJSON() {
    const { type, plugin, version } = this._value;

    return {
      type,
      plugin,
      version,
    };
  }

  static fromJSON(value) {
    const { type, version, plugin } = value;
    return Constraint.create(type, version, plugin);
  }

  static builder() {
    // eslint-disable-next-line no-use-before-define
    return new Builder()
      .plugin('server');
  }
}

class Builder {
  constructor(value = Immutable.Map()) {
    this.value = value;
  }

  type(value) {
    return new Builder(this.value.set('type', value));
  }

  plugin(value) {
    return new Builder(this.value.set('plugin', value));
  }

  version(value) {
    return new Builder(this.value.set('version', value));
  }

  build() {
    const {id} = this.value.toObject();
    return new Constraint(id);
  }
}
