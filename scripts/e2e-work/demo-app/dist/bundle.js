(() => {
  // pipgo-sdk:index
  var __defProp = Object.defineProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var jsx_exports = {};
  __export(jsx_exports, {
    Fragment: () => Fragment,
    FragmentCompat: () => FragmentCompat,
    createElement: () => createElement,
    isFragment: () => isFragment,
    jsx: () => jsx,
    jsxs: () => jsxs
  });
  var Fragment = function Fragment2() {
    return null;
  };
  function jsx(type, props, key) {
    const { children, ...rest } = props ?? {};
    return { type, key, props: children !== void 0 ? { ...rest, children } : rest };
  }
  var jsxs = jsx;
  var createElement = jsx;
  var isFragment = (t) => t === Fragment;
  function FragmentCompat(props) {
    return props?.children ?? null;
  }
  var current = null;
  function __setCurrent(i) {
    current = i;
  }
  var store = globalThis.__PIP_GO_STATE__ ?? (globalThis.__PIP_GO_STATE__ = /* @__PURE__ */ new Map());
  function nextSlot(init) {
    const inst = current;
    const i = inst.cursor++;
    if (i < inst.slots.length) return inst.slots[i];
    const v = init();
    inst.slots[i] = v;
    return v;
  }
  var flush = { scheduled: false };
  function scheduleFlush() {
    if (flush.scheduled) return;
    flush.scheduled = true;
    queueMicrotask(() => {
      flush.scheduled = false;
      globalThis.__pipgo?.__flushRenders?.();
    });
  }
  function useState(initial) {
    const inst = current;
    const i = inst.cursor;
    const slot = nextSlot(() => {
      const v = typeof initial === "function" ? initial() : initial;
      return [
        v,
        (next) => {
          const cur = inst.slots[i][0];
          const nv = typeof next === "function" ? next(cur) : next;
          if (Object.is(cur, nv)) return;
          inst.slots[i][0] = nv;
          scheduleFlush();
        }
      ];
    });
    return slot;
  }
  function __runPendingEffects(inst) {
    const fns = inst.pendingEffects.splice(0);
    for (const fn of fns) queueMicrotask(fn);
  }
  function __seedFromStore(inst) {
    const saved = store.get(inst.keyPath);
    if (saved && saved.length) inst.slots = saved.slice();
  }
  function __persistToStore(inst) {
    if (inst.slots.length) store.set(inst.keyPath, inst.slots.slice());
  }
  function hostTransport() {
    const b = globalThis.__PIP_GO_BRIDGE__;
    if (b && typeof b.post === "function") return b;
    return null;
  }
  function noopTransport() {
    let warned = false;
    return {
      post(json) {
        if (!warned) {
          warned = true;
          console.warn(
            "[pipgo] No native bridge found \u2014 running headless. Render this bundle inside the Pip-Go Android runtime."
          );
        }
        globalThis.__pipgo_ops?.push?.(json);
      }
    };
  }
  function resolveTransport() {
    return hostTransport() ?? noopTransport();
  }
  var pending = /* @__PURE__ */ new Map();
  var transport = null;
  var queue = [];
  var scheduled = false;
  function post(op) {
    queue.push(op);
    if (!scheduled) {
      scheduled = true;
      queueMicrotask(flushNow);
    }
  }
  function flushNow() {
    scheduled = false;
    if (!queue.length) return;
    transport = resolveTransport();
    const ops = queue.splice(0);
    try {
      transport.post(JSON.stringify({ op: "batch", ops }));
    } catch (e) {
      console.error("[pipgo] bridge post failed", e);
    }
  }
  function __resolveCallJson(callId, valueJson, error) {
    const p = pending.get(callId);
    if (!p) return;
    pending.delete(callId);
    if (error != null) p.reject(new Error(error));
    else {
      try {
        p.resolve(valueJson == null ? null : JSON.parse(valueJson));
      } catch (e) {
        p.reject(new Error(`[pipgo] bad response JSON: ${e?.message}`));
      }
    }
  }
  var screenStack = [];
  var screens = /* @__PURE__ */ new Map();
  var navListeners = /* @__PURE__ */ new Set();
  var impl = null;
  function __setNavigationImpl(i) {
    impl = i;
  }
  var Navigation = {
    register(name, component) {
      screens.set(name, component);
    },
    navigate(name, params) {
      const comp = screens.get(name);
      if (!comp) {
        console.error(`[pipgo] Navigation.navigate: unknown screen "${name}"`);
        return;
      }
      const container = `screen:n${screenStack.length}-${Date.now().toString(36)}`;
      const entry = { name, container, params };
      screenStack.push(entry);
      navListeners.forEach((l) => l({ name, params }));
      impl?.navigate(entry);
    },
    back() {
      impl?.back(false);
    },
    current() {
      const top = screenStack[screenStack.length - 1];
      return top ? { name: top.name, params: top.params } : null;
    },
    onNavigate(cb) {
      navListeners.add(cb);
      return () => navListeners.delete(cb);
    },
    /** Boot: register the implicit root screen. */
    __bootRoot() {
      if (!screenStack.length) screenStack.push({ name: "root", container: "screen:0" });
    },
    /** Runtime back button: pop silently, then re-render the new top screen. */
    __nativePop() {
      impl?.back(true);
    },
    __stack: screenStack,
    __screens: screens
  };
  var lifecycle = /* @__PURE__ */ new Map();
  function onLifecycle(name, cb) {
    let set = lifecycle.get(name);
    if (!set) {
      set = /* @__PURE__ */ new Set();
      lifecycle.set(name, set);
    }
    set.add(cb);
    return () => set.delete(cb);
  }
  function __emitLifecycle(name) {
    lifecycle.get(name)?.forEach((cb) => {
      try {
        cb();
      } catch (e) {
        console.error("[pipgo] lifecycle handler error", e);
      }
    });
  }
  var ROOT_CONTAINER = "screen:0";
  var seq = 0;
  var newId = () => `n${++seq}`;
  var eventRegistry = /* @__PURE__ */ new Map();
  function __registerEvents(id2, props) {
    const names = [];
    const map = {};
    for (const [k, v] of Object.entries(props ?? {})) {
      if (typeof v === "function" && k.startsWith("on")) {
        const native = k.slice(2, 3).toLowerCase() + k.slice(3);
        map[native] = v;
        names.push(native);
      }
    }
    if (names.length) eventRegistry.set(id2, map);
    else eventRegistry.delete(id2);
    return names;
  }
  function __dispatchEvent(id2, event, payload) {
    const cb = eventRegistry.get(id2)?.[event];
    if (!cb) return false;
    try {
      cb(payload);
    } catch (e) {
      console.error("[pipgo] event handler error", e);
    }
    return true;
  }
  function commitNow() {
    flushNow();
  }
  function flatten(vnode, out) {
    if (vnode == null || vnode === false || vnode === true) return;
    if (Array.isArray(vnode)) {
      for (const v of vnode) flatten(v, out);
      return;
    }
    out.push(vnode);
  }
  function isFnComponent(type) {
    return typeof type === "function";
  }
  function serializeItemTree(listId, itemKey, vnode) {
    const flat = [];
    flatten(vnode, flat);
    if (!flat.length) return null;
    const ser = (v, prefix) => {
      if (v == null) return null;
      if (typeof v === "string" || typeof v === "number") return { text: String(v) };
      const { children, ...rest } = v.props ?? {};
      const id2 = `${listId}::${itemKey}::${prefix}`;
      __registerEvents(id2, rest);
      const events = Object.keys(rest).filter((k) => typeof rest[k] === "function" && k.startsWith("on")).map((k) => k.slice(2, 3).toLowerCase() + k.slice(3));
      const serializable = {};
      for (const [k, val] of Object.entries(rest)) {
        if (typeof val === "function") continue;
        serializable[k] = val;
      }
      if (events.length) serializable.events = events;
      let childList = null;
      if (Array.isArray(children)) childList = children;
      else if (children != null && typeof children === "object") childList = [children];
      else if (typeof children === "string" || typeof children === "number") serializable.text = String(children);
      let childTrees = null;
      if (childList) {
        childTrees = [];
        childList.forEach((c, i) => {
          const t = ser(c, `${prefix}_${i}`);
          if (t) childTrees.push(t);
        });
        if (!childTrees.length) childTrees = null;
      }
      return { id: id2, c: v.type, p: serializable, ch: childTrees };
    };
    return ser(flat[0], "i0");
  }
  var root = null;
  var rootVNode = null;
  var currentContainer = ROOT_CONTAINER;
  function __setCurrentContainer(id2) {
    currentContainer = id2;
  }
  function makeInstance(fn, keyPath) {
    const inst = {
      fn,
      keyPath,
      slots: [],
      cursor: 0,
      pendingEffects: [],
      mounted: false
    };
    __seedFromStore(inst);
    return inst;
  }
  function renderComponentVnode(vnode, old, keyPath, ops) {
    const fn = vnode.type;
    let inst;
    let node;
    if (old && old.kind === "comp" && old.inst.fn === fn) {
      node = old;
      inst = node.inst;
      inst.cursor = 0;
    } else {
      if (old) removeNode(old, ops);
      inst = makeInstance(fn, keyPath);
      node = { kind: "comp", inst, child: null };
    }
    __setCurrent(inst);
    let out = null;
    try {
      out = inst.fn(vnode.props ?? {});
    } catch (e) {
      __setCurrent(null);
      throw new Error(`[pipgo] component <${fn.name || "anonymous"}> threw: ${e?.message ?? e}`);
    }
    __setCurrent(null);
    const flat = [];
    flatten(out, flat);
    const oldChild = node.child;
    if (!flat.length) {
      if (oldChild) removeNode(oldChild, ops);
      node.child = null;
    } else if (flat.length === 1) {
      node.child = renderAny(flat[0], oldChild, `${keyPath}/0`, ops);
    } else {
      const oldList = oldChild ? oldChild.kind === "group" ? oldChild.children : [oldChild] : [];
      node.child = renderGroup(flat, oldList, keyPath, ops);
    }
    __persistToStore(inst);
    if (!inst.mounted) {
      inst.mounted = true;
    }
    __runPendingEffects(inst);
    return node;
  }
  function renderGroup(vnodes, old, keyPath, ops) {
    const children = [];
    const n = Math.max(vnodes.length, old.length);
    for (let i = 0; i < vnodes.length; i++) {
      const c = renderAny(vnodes[i], old[i] ?? null, `${keyPath}/${i}`, ops);
      if (c) children.push(c);
    }
    for (let i = vnodes.length; i < old.length; i++) removeNode(old[i], ops);
    return { kind: "group", children };
  }
  function renderAny(vnode, old, keyPath, ops) {
    const flat = [];
    flatten(vnode, flat);
    if (!flat.length) {
      if (old) removeNode(old, ops);
      return null;
    }
    if (flat.length === 1) return renderOne(flat[0], old, keyPath, ops);
    const oldList = old ? old.kind === "group" ? old.children : [old] : [];
    return renderGroup(flat, oldList, keyPath, ops);
  }
  function renderOne(vnode, old, keyPath, ops) {
    if (vnode == null || vnode === false) {
      if (old) removeNode(old, ops);
      return null;
    }
    if (isFnComponent(vnode.type)) return renderComponentVnode(vnode, old, keyPath, ops);
    return renderNative(vnode, old, keyPath, ops);
  }
  function renderNative(vnode, old, keyPath, ops) {
    const tag = vnode.type;
    const { children, ...rawProps } = vnode.props ?? {};
    if (old && (old.kind !== "native" || old.tag !== tag)) {
      removeNode(old, ops);
      old = null;
    }
    const node = old ? old : { kind: "native", id: newId(), tag, props: {}, children: [] };
    if (!old) {
      const id2 = node.id;
      const eventNames = __registerEvents(id2, rawProps);
      const props = buildBridgeProps(id2, tag, rawProps, children, eventNames);
      post({ op: "create", id: id2, component: tag, parent: currentContainerOf(ops), before: null, props });
      node.props = props;
    } else {
      const eventNames = __registerEvents(id(node), rawProps);
      const props = buildBridgeProps(id(node), tag, rawProps, children, eventNames);
      const changed = {};
      const allKeys = /* @__PURE__ */ new Set([...Object.keys(props), ...Object.keys(node.props)]);
      for (const k of allKeys) {
        if (k === "items") {
          if (JSON.stringify(props.items) !== JSON.stringify(node.props.items)) changed[k] = props.items;
          continue;
        }
        const a = props[k], b = node.props[k];
        if (JSON.stringify(a ?? null) !== JSON.stringify(b ?? null)) changed[k] = a ?? null;
      }
      if (Object.keys(changed).length) {
        post({ op: "update", id: node.id, props: changed });
        node.props = { ...node.props, ...props };
      }
    }
    node.tag = tag;
    const childVnodes = [];
    flatten(children, childVnodes);
    diffChildren(node, childVnodes, keyPath, ops);
    return node;
  }
  function id(n) {
    return n.id;
  }
  function currentContainerOf(_ops) {
    return currentContainer;
  }
  function diffChildren(node, childVnodes, keyPath, ops) {
    const oldChildren = node.children;
    const allKeyed = childVnodes.length > 1 && childVnodes.every((c) => c && typeof c === "object" && c.key != null);
    if (allKeyed) {
      const oldByKey = /* @__PURE__ */ new Map();
      for (const c of oldChildren) {
        const k = c.kind === "comp" ? String(c.inst.keyPath) : c.vnodeKey;
        if (k) oldByKey.set(k, c);
      }
      const next2 = [];
      childVnodes.forEach((cv, i) => {
        const k = String(cv.key);
        const match = oldByKey.get(k) ?? null;
        const kp = `${keyPath}/k:${k}`;
        const rendered = renderOne(cv, match, kp, ops);
        if (rendered) {
          next2.push(rendered);
          if (rendered.kind === "native") rendered.vnodeKey = k;
          else rendered.vnodeKey = k;
        }
        oldByKey.delete(k);
      });
      for (const [, leftover] of oldByKey) removeNode(leftover, ops);
      node.children = next2;
      return;
    }
    const next = [];
    const n = Math.max(childVnodes.length, oldChildren.length);
    for (let i = 0; i < childVnodes.length; i++) {
      const r = renderAny(childVnodes[i], oldChildren[i] ?? null, `${keyPath}/${i}`, ops);
      if (r) next.push(r);
    }
    for (let i = childVnodes.length; i < oldChildren.length; i++) removeNode(oldChildren[i], ops);
    node.children = next;
  }
  function removeNode(node, ops) {
    if (!node) return;
    if (node.kind === "comp") {
      if (node.child) removeNode(node.child, ops);
      return;
    }
    if (node.kind === "group") {
      for (const c of node.children) removeNode(c, ops);
      return;
    }
    for (const c of node.children) removeNode(c, ops);
    eventRegistry.delete(node.id);
    post({ op: "remove", id: node.id });
  }
  function buildBridgeProps(nodeId, tag, rawProps, children, eventNames) {
    const props = {};
    for (const [k, v] of Object.entries(rawProps ?? {})) {
      if (typeof v === "function") continue;
      if (k === "items") continue;
      props[k] = v;
    }
    if (typeof children === "string" || typeof children === "number") props.text = String(children);
    if (eventNames.length) props.events = eventNames;
    if (tag === "List") {
      const itemsIn = rawProps?.items ?? null;
      const childArr = Array.isArray(children) ? children : null;
      const source = itemsIn ?? childArr;
      if (source) {
        const items = [];
        source.forEach((it, i) => {
          const key = it?.key ?? `i${i}`;
          const vnode = it?.tree ?? (it?.type ? it : null) ?? it;
          const tree = vnode ? serializeItemTree(nodeId, String(key), vnode) : null;
          if (tree) items.push({ key: String(key), tree });
        });
        props.items = items;
      }
    }
    return props;
  }
  function renderRoot(vnode) {
    rootVNode = vnode;
    const ops = [];
    root = renderAny(vnode, root, "root", ops) ?? null;
    commitNow();
  }
  function requestFlush() {
    if (!rootVNode) return;
    const ops = [];
    root = renderAny(rootVNode, root, "root", ops) ?? null;
  }
  function __resetRuntime(keepState) {
    const ops = [];
    if (root) removeNode(root, ops);
    root = null;
    rootVNode = null;
    seq = 0;
    commitNow();
    if (!keepState) {
      globalThis.__PIP_GO_STATE__?.clear?.();
    }
  }
  var SDK_VERSION = "1.0.0";
  var appRoot = null;
  function topContainer() {
    const stack = Navigation.__stack;
    return stack[stack.length - 1]?.container ?? "screen:0";
  }
  function renderTopScreen() {
    const stack = Navigation.__stack;
    const top = stack[stack.length - 1];
    const comp = top?.name === "root" ? null : Navigation.__screens.get(top?.name);
    const vnode = comp ? comp(top?.params ?? {}) : appRoot;
    renderRoot(vnode);
  }
  __setNavigationImpl({
    navigate(entry) {
      __setCurrentContainer(entry.container);
      post({ op: "pushScreen", id: entry.container, name: entry.name, params: entry.params });
      renderTopScreen();
    },
    back(fromNative) {
      const stack = Navigation.__stack;
      if (stack.length <= 1) return;
      stack.pop();
      if (!fromNative) post({ op: "popScreen" });
      __setCurrentContainer(stack[stack.length - 1].container);
      renderTopScreen();
    }
  });
  var App = {
    /**
     * Mount the application. On warm re-execution (fast refresh §25) the old
     * native tree is torn down but hook state is preserved and re-seeded.
     */
    render(el) {
      appRoot = el;
      const warm = !!globalThis.__pipgo;
      if (warm) __resetRuntime(true);
      Navigation.__bootRoot();
      __setCurrentContainer(topContainer());
      renderRoot(typeof el === "function" ? el({}) : el);
    },
    on(name, cb) {
      return onLifecycle(name, cb);
    }
  };
  function installGlobals() {
    const g = globalThis;
    g.__pipgo = {
      version: SDK_VERSION,
      /** id="__app__" → lifecycle; id="__nav__" → back; else node event (§16). */
      dispatchEvent(id2, event, payload) {
        if (id2 === "__app__") return __emitLifecycle(event);
        if (id2 === "__nav__" && event === "back") return Navigation.__nativePop();
        return __dispatchEvent(id2, event, payload);
      },
      fireTimer: (callId) => __resolveCallJson(callId, null, null),
      resolveCallJson: __resolveCallJson,
      __flushRenders: requestFlush
    };
  }
  installGlobals();
  var Text = "Text";
  var Button = "Button";
  var Image = "Image";
  var Column = "Column";
  var Card = "Card";
  var Divider = "Divider";

  // asset:/home/z/my-project/scripts/e2e-work/demo-app/assets/logo.png
  var logo_default = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAWklEQVR4nO3PQQ2AMAwF0LcJzAYzG8xgNhjMYCwYzGAsGMxgLKgK7TrU7e5L6I66qCPUkXrUkXrUkXrUkXrUkXrUkXrUkXrUkXrUkXrUkXrUkXpU6sh3sA+tEB4Tb1AtFgAAAABJRU5ErkJggg==";

  // pipgo-sdk:jsx-runtime
  function jsx2(type, props, key) {
    const { children, ...rest } = props ?? {};
    return { type, key, props: children !== void 0 ? { ...rest, children } : rest };
  }
  var jsxs2 = jsx2;

  // src/App.jsx
  function RootScreen() {
    const [count, setCount] = useState(0);
    return /* @__PURE__ */ jsxs2(Column, { width: "match", height: "match", padding: 24, gravity: "center", children: [
      /* @__PURE__ */ jsx2(Image, { src: logo_default, width: 64, height: 64 }),
      /* @__PURE__ */ jsx2(Text, { text: "Hello Pip-Go", size: 28, fontWeight: "bold" }),
      /* @__PURE__ */ jsx2(Text, { text: "Real native Android UI \u2014 no WebView, no HTML.", size: 14, color: "#94A3B8" }),
      /* @__PURE__ */ jsx2(Divider, { width: "match", margin: 12 }),
      /* @__PURE__ */ jsxs2(Card, { padding: 20, children: [
        /* @__PURE__ */ jsx2(Text, { text: "Counter", size: 16, color: "#64748B" }),
        /* @__PURE__ */ jsx2(Text, { text: `Clicked ${count} times`, size: 22 }),
        /* @__PURE__ */ jsx2(Button, { text: "Continue", onClick: () => setCount(count + 1) })
      ] })
    ] });
  }

  // src/main.jsx
  App.render(/* @__PURE__ */ jsx2(RootScreen, {}));
})();
