package org.vaadin.console.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import org.vaadin.console.Console;
import org.vaadin.console.ObjectInspector;
import org.vaadin.console.Console.Command;

import com.vaadin.Application;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;
import com.vaadin.ui.Button.ClickEvent;

public class ConsoleSampleApplication extends Application {
    private static final long serialVersionUID = 4009702916419936660L;
    protected static final String HELP = "Sample Vaadin shell. Following command are available:\n";
    private ObjectInspector inspector;

    @Override
    public void init() {
        Window mainWindow = new Window("Vaadin Console Demo");
        setMainWindow(mainWindow);

        Panel intro = new Panel("Vaadin Console Demo");
        intro
                .addComponent(new Label(
                        "This console implements a test environment for itself.<br> All methods in console class are exposed as commands in the console itself.",
                        Label.CONTENT_RAW));
        intro
                .addComponent(new Label(
                        "Type 'help' to list all available commands and 'help <command>' to get parameter help.'"));

        // # 1

        // Create a console
        final Console console = new Console();
        mainWindow.addComponent(console);

        // Size and greeting
        console.setPs("}> ");
        console.setCols(80);
        console.setRows(24);
        console.setMaxBufferSize(24);
        console.setGreeting("Welcome to Vaadin console demo.");
        console.reset();
        console.focus();

        // Publish the methods in the Console class itself for testing purposes.
        console.addCommandProvider(inspector = new ObjectInspector(console));

        // Add help command
        Command helpCommand = new Console.Command() {
            private static final long serialVersionUID = 2838665604270727844L;

            public String getUsage(Console console, String[] argv) {
                return argv[0] + " <command>";
            }

            public Object execute(Console console, String[] argv)
                    throws Exception {
                if (argv.length == 2) {
                    Command hc = console.getCommand(argv[1]);
                    ArrayList<String> cmdArgv = new ArrayList<String>(Arrays
                            .asList(argv));
                    cmdArgv.remove(0);
                    return "Usage: "
                            + hc.getUsage(console, cmdArgv
                                    .toArray(new String[] {}));
                }
                return listAvailableCommands();
            }
        };

        // Bind the same command with multiple names
        console.addCommand("help", helpCommand);
        console.addCommand("info", helpCommand);
        console.addCommand("man", helpCommand);
        // #

        // # 2
        Command systemCommand = new Command() {
            private static final long serialVersionUID = -5733237166568671987L;

            public Object execute(Console console, String[] argv)
                    throws Exception {
                Process p = Runtime.getRuntime().exec(argv);
                InputStream in = p.getInputStream();
                StringBuilder o = new StringBuilder();
                InputStreamReader r = new InputStreamReader(in);
                int c = -1;
                try {
                    while ((c = r.read()) != -1) {
                        o.append((char) c);
                    }
                } catch (IOException e) {
                    o.append("[truncated]");
                } finally {
                    if (r != null) {
                        r.close();
                    }
                }
                return o.toString();
            }

            public String getUsage(Console console, String[] argv) {
                // TODO Auto-generated method stub
                return null;
            }
        };

        // #
        console.addCommand("ls", systemCommand);

        // Add sample command
        DummyCmd dummy = new DummyCmd();
        console.addCommand("dir", dummy);
        console.addCommand("cd", dummy);
        console.addCommand("mkdir", dummy);
        console.addCommand("rm", dummy);
        console.addCommand("pwd", dummy);
        console.addCommand("more", dummy);
        console.addCommand("less", dummy);
        console.addCommand("exit", dummy);

        HorizontalLayout pl = new HorizontalLayout();
        pl.setSpacing(true);
        mainWindow.addComponent(pl);
        final TextField input = new TextField(null, "print this");
        pl.addComponent(input);
        pl.addComponent(new Button("print", new Button.ClickListener() {
            private static final long serialVersionUID = 1L;

            public void buttonClick(ClickEvent event) {
                console.print("" + input.getValue());
            }
        }));

        pl.addComponent(new Button("println", new Button.ClickListener() {
            private static final long serialVersionUID = 1L;

            public void buttonClick(ClickEvent event) {
                console.println("" + input.getValue());
            }
        }));

    }

    protected String readToString(InputStream in) {
        StringBuilder o = new StringBuilder();
        InputStreamReader r = new InputStreamReader(in);
        int c = -1;
        try {
            while ((c = r.read()) != -1) {
                o.append((char) c);
            }
        } catch (IOException e) {
            o.append("[truncated]");
        }
        return o.toString();
    }

    public static class DummyCmd implements Console.Command {
        private static final long serialVersionUID = -7725047596507450670L;

        public Object execute(Console console, String[] argv) throws Exception {
            return "Sorry, this is not a real shell and '" + argv[0]
                    + "' is unsupported. Try 'help' instead.";
        }

        public String getUsage(Console console, String[] argv) {
            return "Sorry, this is not a real shell and '" + argv[0]
                    + "' is unsupported. Try 'help' instead.";
        }
    }

    protected String listAvailableCommands() {
        StringBuilder res = new StringBuilder();
        for (String cmd : inspector.getAvailableCommands()) {
            res.append(" ");
            res.append(cmd);
        }
        return res.toString().trim();
    }

}
