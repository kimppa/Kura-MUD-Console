package org.vaadin.console.client.ui;

import org.vaadin.console.client.util.ClientCommUtils;
import org.vaadin.console.client.util.ClientCommUtils.CallHandler;
import org.vaadin.console.client.util.ClientCommUtils.CallableWidget;
import org.vaadin.console.client.util.ClientCommUtils.Transcation;

import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.Paintable;
import com.vaadin.terminal.gwt.client.UIDL;
import com.vaadin.terminal.gwt.client.Util;

/**
 * Vaadin client-side integration to Console GWT Widget.
 * 
 * @author Sami Ekblad / Vaadin
 * 
 */
public class VTextConsole extends TextConsole implements Paintable,
        TextConsoleHandler, CallableWidget {

    private static final String CSS_CLASS_NAME = "v-console";

    private final ClientCommUtils comm = new ClientCommUtils("VTextConsole",
            this);
    private boolean initComplete = false;

    /**
     * The constructor should first call super() to initialize the component and
     * then handle any initialization relevant to Vaadin.
     */
    public VTextConsole() {
        super();
        getElement().addClassName(CSS_CLASS_NAME);
        setHandler(this);

        // Register all server-driven functions
        registerServerCallbacks();
    }

    private void registerServerCallbacks() {
        comm.reg("setGreeting", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                getConfig().setGreeting((String) data[0]);
            }
        });
        comm.reg("setPs", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                setPs((String) data[0]);
            }
        });
        comm.reg("setWrap", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                getConfig().setWrap((Boolean) data[0]);
            }
        });
        comm.reg("setRows", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                getConfig().setRows((Integer) data[0]);
                setRows((Integer) data[0]);
            }
        });
        comm.reg("setCols", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                getConfig().setCols((Integer) data[0]);
                setCols((Integer) data[0]);
            }
        });
        comm.reg("print", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                print((String) data[0]);
            }
        });
        comm.reg("println", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                println((String) data[0]);
            }
        });
        comm.reg("println", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                println((String) data[0]);
            }
        });
        comm.reg("prompt", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                prompt((String) (data.length > 0 ? data[0] : null));
            }
        });
        comm.reg("ff", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                formFeed();
            }
        });
        comm.reg("cr", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                carriageReturn();
            }
        });
        comm.reg("lf", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                carriageReturn();
            }
        });
        comm.reg("clearBuffer", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                clearBuffer();
            }
        });
        comm.reg("reset", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                reset();
            }
        });
        comm.reg("newLine", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                newLine();
            }
        });
        comm.reg("scrollToEnd", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                scrollToEnd();
            }
        });
        comm.reg("bell", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                bell();
            }
        });
        comm.reg("setMaxBufferSize", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                getConfig().setMaxBufferSize((Integer) data[0]);
                setMaxBufferSize((Integer) data[0]);
            }
        });
        comm.reg("clearHistory", new CallHandler() {
            public void call(final String methodName, final Object[] data) {
                clearCommandHistory();
            }
        });
    }

    /**
     * Called whenever an update is received from the server
     */
    public void updateFromUIDL(final UIDL serverData,
            final ApplicationConnection appConn) {
        if (appConn.updateComponent(this, serverData, true)) {
            return;
        }

        comm.updateComponent(serverData, appConn);

        // Dispatch all method calls from the server
        // comm.callMethods(serverData.getChildByTagName("calls"));

    }

    private void sendSizeToServer() {
        if (!initComplete) {
            return;
        }

        final Transcation tx = comm.startTx();
        tx.send("height", getHeight());
        tx.send("width", getWidth());
        tx.commit();
    }

    public void terminalInput(final TextConsole term, final String input) {
        if (!initComplete) {
            return;
        }

        final Transcation tx = comm.startTx();
        tx.send("input", input);
        tx.commit();
    }

    @Override
    protected void calculateColsFromWidth() {
        final int oldCols = getCols();
        super.calculateColsFromWidth();

        if (!initComplete) {
            return;
        }

        if (getCols() != oldCols) {
            final Transcation tx = comm.startTx();
            tx.send("cols", getCols());
            tx.commit();
        }
    }

    @Override
    protected void calculateRowsFromHeight() {
        final int oldRows = getRows();
        super.calculateRowsFromHeight();

        if (!initComplete) {
            return;
        }

        if (getRows() != oldRows) {
            final Transcation tx = comm.startTx();
            tx.send("rows", getRows());
            tx.commit();
        }
    }

    @Override
    protected void calculateHeightFromRows() {
        super.calculateHeightFromRows();
        notifyPaintableSizeChange();
        sendSizeToServer();
    }

    @Override
    protected void calculateWidthFromCols() {
        super.calculateWidthFromCols();
        notifyPaintableSizeChange();
        sendSizeToServer();
    }

    private void notifyPaintableSizeChange() {
        Util.notifyParentOfSizeChange(this, false);
    }

    @Override
    protected void suggest() {
        if (!initComplete) {
            return;
        }

        final String input = getInput();
        final Transcation tx = comm.startTx();
        tx.send("suggest", input);
        tx.commit();
    }

    public void initClientWidget(final Object[] params) {
        initComplete = true;

        // Console configuration
        final TextConsoleConfig cfg = getConfig();
        int i = 0;
        cfg.setCols((Integer) params[i++]);
        cfg.setRows((Integer) params[i++]);
        cfg.setMaxBufferSize((Integer) params[i++]);
        cfg.setWrap((Boolean) params[i++]);
        cfg.setGreeting((String) params[i++]);
        cfg.setPs((String) params[i++]);
        comm.d("init: '" + cfg.getGreeting() + "';" + cfg.getCols() + "x"
                + cfg.getRows() + "");
        setConfig(cfg);

        reset();
    }

    @Override
    public void setConfig(final TextConsoleConfig cfg) {
        // Wrap everything to a single tx
        final Transcation tx = comm.startTx();
        super.setConfig(cfg);
        tx.commit();
    }

    public void serverCalls(final String method, final Object[] params) {
        comm.d("Uknown method: " + method);
    }

}
