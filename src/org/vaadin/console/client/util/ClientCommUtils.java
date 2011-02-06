package org.vaadin.console.client.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.UIDL;

public class ClientCommUtils {

    public static final int PARAM_STRING = 0;
    public static final int PARAM_BOOLEAN = 1;
    public static final int PARAM_INT = 2;
    public static final int PARAM_FLOAT = 3;
    public static final int PARAM_MAP = 4;
    public static final int PARAM_RESOURCE = 5;

    private static final String DEFAULT_DEBUG_ID = "ClientCommUtils";
    public static final String CLIENT_INIT = "_init";
    private static final String SERVER_CALL_PREFIX = "c_";
    private static final String SERVER_CALL_PARAM_PREFIX = "p_";
    private static final String SERVER_CALL_SEPARATOR = "_";
    private static final String SERVER_HAS_SENT_THE_INIT = "_si";

    private String debugId;
    private ApplicationConnection appConn;
    private String id;
    private boolean immediate;
    private HashMap<String, CallHandler> callHandlers = new HashMap<String, CallHandler>();
    private Transcation tx;
    private List<MethodCall> postponedCallsFromServer;
    private CallableWidget clientWidget;
    private boolean postpone;
    private boolean requestInitFromServer;
    private int callCounter;
    private CallHandler dh = new CallHandler() {

        public void call(String methodName, Object[] params) {
            clientWidget.serverCalls(methodName, params);
        }
    };
    private boolean clientWasInitialized;

    public interface CallableWidget {

        public void initClientWidget(Object[] params);

        public void serverCalls(String method, Object[] params);

    }

    public ClientCommUtils(CallableWidget clientWidget) {
        this(DEFAULT_DEBUG_ID, clientWidget);
    }

    public ClientCommUtils(String debugId, CallableWidget clientWidget) {
        this.debugId = debugId;
        this.clientWidget = clientWidget;
    }

    public static boolean isDebug() {
        return ApplicationConnection.isDebugMode();
    }

    public static void log(String msg) {
        ApplicationConnection.getConsole().log(msg);
    }

    public boolean debug() {
        return isDebug();
    }

    public void d(String msg) {
        if (debug()) {
            debug(msg);
        }
    }

    public void debug(String msg) {
        debug(debugId, id, msg);
    }

    private void debug(String debugId, String instanceId, String msg) {
        log("[" + debugId + ":" + instanceId + "] " + msg);
    }

    public void setAppConn(ApplicationConnection client) {
        appConn = client;
    }

    public ApplicationConnection getAppConn() {
        return appConn;
    }

    public void setId(String id) {
        this.id = id;
        if (debugId.equals(DEFAULT_DEBUG_ID)) {
            debugId = id;
        }
    }

    public String getId() {
        return id;
    }

    public void setImmediate(boolean immediate) {
        this.immediate = immediate;
    }

    public boolean isImmediate() {
        return immediate;
    }

    private void send(String name, String value, boolean now) {
        d("Send " + name + " = " + value);
        getAppConn().updateVariable(getId(), name, value, now);
    }

    private void send(String name, int value, boolean now) {
        d("Send " + name + " = " + value);
        getAppConn().updateVariable(getId(), name, value, now);
    }

    private void send(String name, float value, boolean now) {
        d("Send " + name + " = " + value);
        getAppConn().updateVariable(getId(), name, value, now);
    }

    private void send(String name, boolean value, boolean now) {
        d("Send " + name + " = " + value);
        getAppConn().updateVariable(getId(), name, value, now);
    }

    private void send(String name, String[] value, boolean now) {
        d("Send " + name + " = String[" + value.length + "]");
        getAppConn().updateVariable(getId(), name, value, now);
    }

    private void send(String name, Map<String, Object> value, boolean now) {
        d("Send " + name + " = " + value);
        getAppConn().updateVariable(getId(), name, value, now);
    }

    public native String debug(JavaScriptObject obj) /*-{
                                                     var s = "";
                                                     for (var i in obj) {
                                                     if (typeof obj[i] != "function") {
                                                     s += i + " = " + obj[i]+ "\n";
                                                     } else {
                                                     s += i + " = [function]\n";
                                                     }
                                                     }
                                                     return s;
                                                     }-*/;

    /**
     * Copy UIDL attribute values to JavaScriptObject as properties.
     * 
     * @param toObject
     *            Javascript object to assign to
     * @param uidl
     *            Parent node.
     * @param nodeTag
     *            Tag name of the data.
     * @return true if something was copied. False otherwise.
     */
    public static boolean updateJavaScriptObject(JavaScriptObject toObject,
            UIDL uidl, String nodeTag) {
        UIDL node = uidl.getChildByTagName(nodeTag);
        if (node != null) {
            Set<String> keys = node.getAttributeNames();
            JavaScriptObject fromObject = getUIDLAttributes(node);
            for (String key : keys) {
                copyJavaScriptAttribute(key, fromObject, toObject);
            }
            return !keys.isEmpty();
        }
        return false;
    }

    private static native void copyJavaScriptAttribute(String key,
            JavaScriptObject from, JavaScriptObject to)/*-{
                                                       to[key] = from[key];
                                                       }-*/;

    private static native JavaScriptObject getUIDLAttributes(UIDL uidl) /*-{
                                                                        return uidl[1];
                                                                        }-*/;

    /** Register a method handler for server-driven calls. */
    public void reg(String methodName, CallHandler method) {
        callHandlers.put(methodName, method);
        d("Registered '" + methodName + "'");
    }

    public boolean callMethods(UIDL calls) {
        d("Processing calls from the server");
        if (calls == null) {
            d("No calls to process");
            return false;
        }

        // Iterate th UIDL childs and add to process queue
        Iterator<Object> i = calls.getChildIterator();
        List<MethodCall> callsFromServer = null;
        if (postponedCallsFromServer != null) {
            callsFromServer = postponedCallsFromServer; // append
        } else {
            callsFromServer = new ArrayList<MethodCall>();
        }
        while (i.hasNext()) {
            UIDL callData = (UIDL) i.next();
            String methodName = callData.getStringAttribute("n");

            // Client init immediately (before anything else is called)
            if (CLIENT_INIT.equals(methodName)) {
                d("Init received from server");
                requestInitFromServer = false; // cancel the pending init
                                               // request,
                // if any
                clientWidget.initClientWidget(getParams(callData).toArray());
                continue;
            }

            CallHandler ch = callHandlers.get(methodName);
            if (ch != null) {
                d("Found handler for '" + methodName + "'");
                callsFromServer.add(new MethodCall(methodName, ch,
                        getParams(callData)));
            } else {
                d("Using default handler for '" + methodName + "'");
                callsFromServer.add(new MethodCall(methodName, dh,
                        getParams(callData)));
            }
        }
        boolean ret = false;
        if (isPostponeCallsFromServer()) {
            d("Postponed " + callsFromServer.size() + " calls from the server");
            postponedCallsFromServer = callsFromServer;
        } else {
            d("Received " + callsFromServer.size()
                    + " calls from the server to be processed.");
            postponedCallsFromServer = null;
            ret = callMethods(callsFromServer);
        }
        return ret;
    }

    public boolean callPostponedMethods() {
        if (postponedCallsFromServer != null && !isPostponeCallsFromServer()) {
            List<MethodCall> toCall = postponedCallsFromServer;
            postponedCallsFromServer = null;
            d("Processing " + toCall.size() + " postponed calls");
            return callMethods(toCall);
        }
        return false;
    }

    private boolean callMethods(List<MethodCall> calls) {
        if (calls != null && !calls.isEmpty()) {
            d("Processing " + calls.size() + " calls");
            for (MethodCall c : calls) {
                c.exec();
            }
            return true;
        }
        return false;
    }

    public void setPostponeCalls(boolean postponeCalls) {
        postpone = postponeCalls;
        d("Postpone: " + postponeCalls);
    }

    public boolean isPostponeCallsFromServer() {
        return postpone;
    }

    public interface CallHandler {

        void call(String methodName, Object[] params);
    }

    public class VariableChange {

        private String vn;
        private Integer vi;
        private Float vf;
        private String vs;
        private Boolean vb;
        private boolean immediate;
        private String[] vsa;
        private Map<String, Object> vm;

        public VariableChange(String variableName, int value) {
            vn = variableName;
            vi = value;
        }

        public VariableChange(String variableName, Map<String, Object> value) {
            vn = variableName;
            vm = value;
        }

        public void sendToServer(boolean now) {
            if (vs != null) {
                ClientCommUtils.this.send(vn, vs, now && immediate);
            }
            if (vi != null) {
                ClientCommUtils.this.send(vn, vi, now && immediate);
            }
            if (vb != null) {
                ClientCommUtils.this.send(vn, vb, now && immediate);
            }
            if (vf != null) {
                ClientCommUtils.this.send(vn, vf, now && immediate);
            }
            if (vsa != null) {
                ClientCommUtils.this.send(vn, vsa, now && immediate);
            }
            if (vm != null) {
                ClientCommUtils.this.send(vn, vm, now && immediate);
            }
        }

        public VariableChange(String variableName, String value) {
            vn = variableName;
            vs = value;
        }

        public VariableChange(String variableName, boolean value) {
            vn = variableName;
            vb = value;
        }

        public VariableChange(String variableName, float value) {
            vn = variableName;
            vf = value;
        }

        public VariableChange(String name, String[] s) {
            vn = name;
            vsa = s;
        }

        public Object getValue() {
            Object v = vi;
            if (v == null) {
                v = vs;
            }
            if (v == null) {
                v = vb;
            }
            if (v == null) {
                v = vf;
            }
            if (v == null) {
                v = vsa;
            }
            return v;
        }

        public void setImmediate(boolean immediate) {
            this.immediate = immediate;
        }
    }

    public class MethodCall {

        private String method;
        private CallHandler cb;
        private List<Object> params;

        public MethodCall(String method, CallHandler cb, List<Object> params) {
            this.method = method;
            this.cb = cb;
            this.params = params;
        }

        public void exec() {
            if (debug()) {
                d("Calling " + method + "(" + debugArray(params) + ")");
            }
            cb.call(method, params.toArray());
        }
    }

    private List<Object> getParams(UIDL callNode) {
        int pc = callNode.hasAttribute("pc") ? callNode.getIntAttribute("pc")
                : 0;
        List<Object> params = new ArrayList<Object>();
        for (int i = 0; i < pc; i++) {
            String pn = "p" + i;
            if (callNode.hasAttribute(pn) && callNode.hasAttribute("pt" + i)) {
                int pt = callNode.getIntAttribute("pt" + i);
                if (pt == ClientCommUtils.PARAM_STRING
                        || pt == ClientCommUtils.PARAM_RESOURCE) {
                    params.add(callNode.getStringAttribute(pn));
                } else if (pt == ClientCommUtils.PARAM_BOOLEAN) {
                    params.add(callNode.getBooleanAttribute(pn));
                } else if (pt == ClientCommUtils.PARAM_INT) {
                    params.add(callNode.getIntAttribute(pn));
                } else if (pt == ClientCommUtils.PARAM_FLOAT) {
                    params.add(callNode.getFloatAttribute(pn));
                } else if (pt == ClientCommUtils.PARAM_MAP) {
                    params.add(callNode.getMapAttribute(pn));
                } else {
                    d("Invalid parameter type '" + pt + "' for '"
                            + callNode.getStringAttribute("name")
                            + "' parameter " + i);
                }
            } else {
                d("Missing call parameter " + i + " for "
                        + callNode.getStringAttribute("name"));
            }
        }
        return params;
    }

    protected String debugArray(List<Object> params) {
        StringBuilder v = new StringBuilder();
        boolean first = true;
        for (Object p : params) {
            if (!first) {
                v.append(",");
            }
            first = false;
            v.append(p);
        }
        return v.toString();
    }

    public void init(ApplicationConnection appConn, UIDL uidl) {
        setAppConn(appConn);
        setId(uidl.getId());
        setImmediate(uidl.hasAttribute("immediate")
                && uidl.getBooleanAttribute("immediate"));
    }

    public Transcation startTx() {
        d("Start transaction");
        if (tx == null) {
            d("New transaction created");
            tx = new Transcation();
        } else {
            tx.nestedLevel++;
        }
        return tx;
    }

    public Transcation currentTx() {
        return tx;
    }

    public class Transcation {

        List<VariableChange> data = new ArrayList<VariableChange>();
        private int nestedLevel;

        public void send(VariableChange vc) {
            data.add(vc);
            vc.immediate = false;
        }

        public void send(String name, String value) {
            send(new VariableChange(name, value));
        }

        public void send(String name, boolean value) {
            send(new VariableChange(name, value));
        }

        public void send(String name, int value) {
            send(new VariableChange(name, value));
        }

        public void send(String name, float value) {
            send(new VariableChange(name, value));
        }

        public void send(String name, Map<String, Object> value) {
            send(new VariableChange(name, value));
        }

        public void send(String name, JsArrayString value) {
            String[] s = new String[value.length()];
            for (int i = 0; i < s.length; i++) {
                s[i] = value.get(i);
            }
            send(new VariableChange(name, s));
        }

        public void send(String name, String[] value) {
            send(new VariableChange(name, value));
        }

        public void commit() {
            d("Commit transaction size=" + data.size());
            if (nestedLevel > 0) {
                d("Nested transaction commit to parent. level=" + nestedLevel);
                nestedLevel--;
                return;
            }
            tx = null;
            for (VariableChange v : data) {
                v.sendToServer(false);
            }
            sync();
        }

        public boolean isNested() {
            return nestedLevel > 0;
        }

        public void cancel() {
            d("Cancel transaction size=" + data.size());
            tx = null;
        }

        @SuppressWarnings("unchecked")
        public void call(String method, Object[] params) {
            int cid = callCounter++;
            send(SERVER_CALL_PREFIX + cid + SERVER_CALL_SEPARATOR + method,
                    true);
            int i = 0;
            for (Object p : params) {
                String vn = SERVER_CALL_PARAM_PREFIX + cid
                        + SERVER_CALL_SEPARATOR + (i++);
                if (p instanceof Boolean) {
                    send(vn, (Boolean) p);
                } else if (p instanceof String) {
                    send(vn, (String) p);
                } else if (p instanceof Integer) {
                    send(vn, (Integer) p);
                } else if (p instanceof Float) {
                    send(vn, (Float) p);
                } else if (p instanceof Map<?, ?>) {
                    send(vn, (Map<String, Object>) p);
                }
            }
        }
    }

    public void forceSync() {
        d("Force sync");
        appConn.sendPendingVariableChanges();
    }

    public void sync() {
        if (isImmediate()) {
            d("Syncing with the server");
            appConn.sendPendingVariableChanges();
        }
    }

    public void requestServerInit() {
        if (id == null) {
            // Too early to request. Check what the init brings up
            requestInitFromServer = true;
        } else {
            d("Requesting client init.");
            requestInitFromServer = false;
            send(CLIENT_INIT, true, true);
        }
    }

    public void call(String method, Object... params) {
        // Calls always in a single transaction
        Transcation t = startTx();
        tx.call(method, params);
        t.commit();
    }

    public void updateComponent(final UIDL uidl,
            final ApplicationConnection client) {
        d("Update from the server");
        clientWasInitialized = getId() != null;

        // Ensure the communication module itself
        init(client, uidl);

        boolean shouldBeInitialized = uidl
                .hasAttribute(SERVER_HAS_SENT_THE_INIT);
        requestInitFromServer = !clientWasInitialized && shouldBeInitialized;

        // Call all methods (this inits also if received)
        callMethods(uidl.getChildByTagName("cl"));

        // If there is a pending init request, proces it.
        if (requestInitFromServer) {
            send(CLIENT_INIT, true, true);
        }
    }

}
