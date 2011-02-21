package org.vaadin.console.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.terminal.PaintException;
import com.vaadin.terminal.PaintTarget;
import com.vaadin.terminal.Resource;

public class ServerCommUtils implements Serializable {

    public static final int PARAM_STRING = 0;
    public static final int PARAM_BOOLEAN = 1;
    public static final int PARAM_INT = 2;
    public static final int PARAM_FLOAT = 3;
    public static final int PARAM_MAP = 4;
    public static final int PARAM_RESOURCE = 5;

    private static final String SERVER_CALL_PREFIX = "c_";
    private static final String SERVER_CALL_PARAM_PREFIX = "p_";
    private static final String SERVER_CALL_SEPARATOR = "_";
    private static final String SERVER_HAS_SENT_THE_INIT = "_si";

    private static final long serialVersionUID = 4687944475579171126L;
    private static final String CLIENT_INIT = "_init";

    private CallableComponent component;
    private List<Object[]> clientCallQueue = new ArrayList<Object[]>();
    private boolean pendingClientInit;
    private Object[] clientInitParams;
    private boolean initSent;

    private Map<String, String> styles = new HashMap<String, String>();

    public interface CallableComponent extends Serializable {

        public void clientRequestedInit();

        public void clientCalls(String method, Object[] params);

        public void requestRepaint();
    }

    public ServerCommUtils(CallableComponent component) {
        this.component = component;
        requestClientWidgetInit();
    }

    private void receiveCallsFromClient(Map<String, Object> variables) {
        // Handle init first
        if (variables.containsKey(CLIENT_INIT) && clientInitParams == null) {
            pendingClientInit = false;
            component.clientRequestedInit();
            initSent = true;
        }

        // Other calls
        for (String n : variables.keySet()) {
            if (n.startsWith(SERVER_CALL_PREFIX)) {
                String cidStr = n.substring(SERVER_CALL_PREFIX.length(), n
                        .indexOf(SERVER_CALL_SEPARATOR, SERVER_CALL_PREFIX
                                .length() + 1));
                int cid = Integer.parseInt(cidStr);
                n = n.substring(SERVER_CALL_PREFIX.length()
                        + ("" + cid).length() + 1);
                List<Object> params = new ArrayList<Object>();
                int i = 0;
                String pn = SERVER_CALL_PARAM_PREFIX + cid
                        + SERVER_CALL_SEPARATOR + i;
                while (variables.containsKey(pn)) {
                    params.add(variables.get(pn));
                    pn = SERVER_CALL_PARAM_PREFIX + cid + SERVER_CALL_SEPARATOR
                            + (++i);
                }
                component.clientCalls(n, params.toArray());
            }
        }
    }

    public void call(String method, Object... param) {
        queueClientCall(method, param);
        component.requestRepaint();
    }

    public void paintContent(PaintTarget target) throws PaintException {

        // TODO: Validate this behavior
        // Ask init 1) when explicitly asked 2) when no client calls has been
        // made AND no pending init data is available
        if ((pendingClientInit && !initSent) && clientInitParams == null) {
            pendingClientInit = false;
            component.clientRequestedInit();
            initSent = true;
        }

        // Notify client that init data should have been there already
        if (initSent) {
            target.addAttribute(SERVER_HAS_SENT_THE_INIT, true);
        }

        target.startTag("cl");

        // Paint init first
        if (clientInitParams != null) {
            target.startTag("c");
            target.addAttribute("n", CLIENT_INIT);
            paintCallParameters(target, clientInitParams, 0);
            target.endTag("c");
            clientInitParams = null;
        }

        try {
            ArrayList<Object[]> tmpCalls = new ArrayList<Object[]>(
                    clientCallQueue); // copy
            for (Object[] aCall : tmpCalls) {
                target.startTag("c");
                target.addAttribute("n", (String) aCall[0]);
                paintCallParameters(target, aCall, 1);
                target.endTag("c");
                clientCallQueue.remove(aCall);
            }
        } catch (Throwable e) {
            throw new PaintException(e.getMessage());
        } finally {
            target.endTag("cl");
        }
    }

    private void paintCallParameters(PaintTarget target, Object[] aCall,
            int start) throws PaintException {
        target.addAttribute("pc", aCall.length - start);
        for (int i = start; i < aCall.length; i++) {
            if (aCall[i] != null) {
                int pi = i - start; // index parameters from start
                paintCallParameter(target, aCall[i], pi);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void paintCallParameter(PaintTarget target, Object p, int pi)
            throws PaintException {
        if (p instanceof String) {
            p = replaceTags(p.toString());
            target.addAttribute("p" + pi, (String) p);
            target.addAttribute("pt" + pi, PARAM_STRING);
        } else if (p instanceof Float) {
            target.addAttribute("p" + pi, (Float) p);
            target.addAttribute("pt" + pi, PARAM_FLOAT);
        } else if (p instanceof Boolean) {
            target.addAttribute("p" + pi, (Boolean) p);
            target.addAttribute("pt" + pi, PARAM_BOOLEAN);
        } else if (p instanceof Integer) {
            target.addAttribute("p" + pi, (Integer) p);
            target.addAttribute("pt" + pi, PARAM_INT);
        } else if (p instanceof Map) {
            target.addAttribute("p" + pi, (Map) p);
            target.addAttribute("pt" + pi, PARAM_MAP);
        } else if (p instanceof Resource) {
            target.addAttribute("p" + pi, (Resource) p);
            target.addAttribute("pt" + pi, PARAM_RESOURCE);
        }
    }

    public void changeVariables(Object source, Map<String, Object> variables) {
        receiveCallsFromClient(variables);
    }

    private synchronized void queueClientCall(String method, Object... params) {
        Object[] call = new Object[params.length + 1];
        call[0] = method;
        for (int i = 0; i < params.length; i++) {
            call[i + 1] = params[i];
        }
        clientCallQueue.add(call);
    }

    public void requestClientWidgetInit() {
        initSent = false;
        if (!pendingClientInit) {
            pendingClientInit = true;
            component.requestRepaint();
        }
    }

    public void initClientWidget(Object... params) {
        clientInitParams = params;
        pendingClientInit = false;
        component.requestRepaint();
    }

    public List<String> getUnsentCalls() {
        // TODO: Slow. Needed to check if some call is pending.
        ArrayList<String> res = new ArrayList<String>();
        for (Object[] c : clientCallQueue) {
            res.add((String) c[0]);
        }
        return res;
    }

    public void cancelCalls(String methodName) {
        // TODO: Slow. Needed to cancel/replace a pending call with another
        // call.
        ArrayList<Object[]> tmp = new ArrayList<Object[]>(clientCallQueue);
        for (Object[] c : tmp) {
            if (c[0].equals(methodName)) {
                clientCallQueue.remove(c);
            }
        }
    }

    public void addStyle(String tagName, String style) {
        styles.put(tagName, style);
    }

    public void removeStyle(String tagName) {
        styles.remove(tagName);
    }

    private String replaceTags(String string) {
        for (String tagName : styles.keySet()) {
            String startTag = "\\[" + tagName + "\\]";
            String startTagReplacement = "<span class=\"" + styles.get(tagName)
                    + "\">";
            string = string.replaceAll(startTag, startTagReplacement);

            String endTag = "\\[/" + tagName + "\\]";
            String endTagReplacement = "</span>";
            string = string.replaceAll(endTag, endTagReplacement);
        }

        return string;
    }
}
