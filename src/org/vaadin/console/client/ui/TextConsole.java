package org.vaadin.console.client.ui;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FocusWidget;
import com.vaadin.terminal.gwt.client.Util;

/**
 * GWT Console Widget.
 * 
 * @author Sami Ekblad / Vaadin
 * 
 */
public class TextConsole extends FocusWidget {

    /* Control characters in http://en.wikipedia.org/wiki/Control_character */

    public static final char CTRL_BELL = 'G';
    public static final char CTRL_BACKSPACE = 'H';
    public static final char CTRL_TAB = 'I';
    public static final char CTRL_LINE_FEED = 'J';
    public static final char CTRL_FORM_FEED = 'L';
    public static final char CTRL_CARRIAGE_RETURN = 'M';
    public static final char CTRL_ESCAPE = '[';
    public static final char CTRL_DELETE = '?';

    private static final char[] CTRL = { CTRL_BELL, CTRL_BACKSPACE, CTRL_TAB,
            CTRL_LINE_FEED, CTRL_FORM_FEED, CTRL_CARRIAGE_RETURN, CTRL_ESCAPE,
            CTRL_DELETE };

    public static char getControlKey(final int kc) {
        for (final char c : CTRL) {
            if (kc == c) {
                return c;
            }
        }
        return 0;
    }

    private static final String DEFAULT_TABS = "    ";
    private static final int BIG_NUMBER = 100000;
    private final DivElement term;
    private TextConsoleConfig config;
    private TextConsoleHandler handler;
    private final Element buffer;
    private final TableElement prompt;
    private final Element ps;
    private final InputElement input;
    private List<String> cmdHistory = new ArrayList<String>();
    private int cmdHistoryIndex = -1;
    private final HandlerRegistration clickHandler;
    private final HandlerRegistration keyHandler;
    private HandlerRegistration focusHandler;
    private int fontW = -1;
    private int fontH = -1;
    private int scrollbarW = -1;
    private int rows;
    private int cols;
    private String tabs = DEFAULT_TABS;
    private boolean focused;
    private int promptRows;
    private int padding;
    private final DivElement promptWrap;
    private Timer timer;
    private int maxBufferSize;
    private String cleanPs;
    private int paddingW;

    public TextConsole() {

        // Main element
        term = Document.get().createDivElement();
        term.addClassName("term");
        setElement(term);
        setTabIndex(0);

        // Buffer
        buffer = Document.get().createElement("pre");
        buffer.addClassName("b");
        term.appendChild(buffer);

        // Prompt elements
        promptWrap = Document.get().createDivElement();
        promptWrap.addClassName("pw");
        term.appendChild(promptWrap);

        prompt = Document.get().createTableElement();
        promptWrap.appendChild(prompt);
        prompt.setAttribute("cellpadding", "0");
        prompt.setAttribute("cellspacing", "0");
        prompt.setAttribute("border", "0");
        prompt.addClassName("p");

        final TableSectionElement tbody = Document.get().createTBodyElement();
        prompt.appendChild(tbody);

        final TableRowElement tr = Document.get().createTRElement();
        tbody.appendChild(tr);

        final TableCellElement psTd = Document.get().createTDElement();
        psTd.addClassName("psw");
        tr.appendChild(psTd);

        ps = Document.get().createElement("nobr");
        ps.addClassName("ps");
        psTd.appendChild(ps);

        final TableCellElement inputTd = Document.get().createTDElement();
        inputTd.addClassName("iw");
        tr.appendChild(inputTd);

        input = (InputElement) Document.get().createElement("input");
        inputTd.appendChild(input);
        input.addClassName("i");
        input.setTabIndex(-1);
        input.setAttribute("spellcheck", "false");

        config = TextConsoleConfig.newInstance();

        setPromtActive(false);

        clickHandler = addDomHandler(new ClickHandler() {

            public void onClick(final ClickEvent event) {
                setFocus(true);
            }
        }, ClickEvent.getType());

        keyHandler = addDomHandler(new KeyDownHandler() {

            public void onKeyDown(final KeyDownEvent event) {

                // (re-)show the prompt
                setPromtActive(true);

                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    event.preventDefault();
                    carriageReturn();
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_UP
                        || event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
                    event.preventDefault();
                    handleCommandHistoryBrowse(event.getNativeKeyCode());
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
                    event.preventDefault();
                    suggest();
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_BACKSPACE
                        && getInputLenght() == 0) {
                    bell();
                } else if (event.getNativeEvent().getCtrlKey()) {
                    final char ctrlChar = getControlKey(event
                            .getNativeKeyCode());
                    if (ctrlChar > 0) {
                        event.preventDefault();
                        handleControlChar(ctrlChar);
                    }
                }

            }
        }, KeyDownEvent.getType());

        updateFontDimensions();
    }

    protected int getInputLenght() {
        final String v = input.getValue();
        if (v != null) {
            return v.length();
        }
        return -1;
    }

    protected void handleControlChar(final char c) {
        switch (c) {
        case TextConsole.CTRL_BACKSPACE:
            backspace();
            break;
        case TextConsole.CTRL_BELL:
            bell();
            break;
        case TextConsole.CTRL_CARRIAGE_RETURN:
            carriageReturn();
            break;
        case TextConsole.CTRL_DELETE:
            bell(); // TODO: not supported yet
            break;
        case TextConsole.CTRL_ESCAPE:
            bell(); // TODO: not supported yet
            break;
        case TextConsole.CTRL_FORM_FEED:
            formFeed();
            break;
        case TextConsole.CTRL_LINE_FEED:
            lineFeed();
            break;
        case TextConsole.CTRL_TAB:
            tab();
            break;

        default:
            bell();
            break;
        }
    }

    protected void suggest() {
        // No suggest by default. Implement by subclassing.
    }

    protected void handleCommandHistoryBrowse(final int i) {
        cmdHistoryIndex = i == KeyCodes.KEY_UP ? cmdHistoryIndex - 1
                : cmdHistoryIndex + 1;
        if (cmdHistoryIndex >= 0 && cmdHistoryIndex < cmdHistory.size()) {
            prompt(cmdHistory.get(cmdHistoryIndex));
        } else {
            prompt();
        }
    }

    public String getInput() {
        return input.getValue();
    }

    protected void setInput(final String inputText) {
        if (inputText != null) {
            input.setValue(inputText);
        } else {
            input.setValue("");
        }
        if (isFocused()) {
            focusPrompt();
        }
    }

    public void lineFeed() {
        carriageReturn();
    }

    protected void tab() {
        prompt(getInput() + "\t");
    }

    protected void backspace() {
        bell();
    }

    protected void carriageReturn() {
        setPromtActive(false);
        // Append newline first if not there yet
        if (!bufferIsEmpty() && !bufferEndsWithNewLine()) {
            newLine();
            reducePrompt(-1);
        }
        print(getCurrentPromptContent());
        newLine();
        if (promptRows > 1) {
            reducePrompt(-1);
        }
        String lineBuffer = getInput();
        lineBuffer = lineBuffer.trim();
        if (!"".equals(lineBuffer)) {
            cmdHistory.add(lineBuffer);
            cmdHistoryIndex = cmdHistory.size();
        }
        if (handler != null) {
            handler.terminalInput(this, lineBuffer);
        }
    }

    private boolean bufferIsEmpty() {
        return !buffer.hasChildNodes();
    }

    private void setPromtActive(final boolean active) {
        if (active && !isPromptActive()) {
            prompt.getStyle().setDisplay(Display.BLOCK);
        } else if (!active && isPromptActive()) {
            prompt.getStyle().setDisplay(Display.NONE);
        }
    }

    private boolean isPromptActive() {
        return !Display.NONE.getCssName()
                .equals(prompt.getStyle().getDisplay());
    }

    private boolean isFocused() {
        return focused;
    }

    public void setConfig(final TextConsoleConfig cfg) {
        config = cfg;

        updateFontDimensions();

        scrollbarW = getScrollbarWidth();
        final String padStr = term.getStyle().getPadding();
        if (padStr != null && padStr.endsWith("px")) {
            padding = Integer
                    .parseInt(padStr.substring(0, padStr.length() - 2));
        } else {
            // _log("using default padding: 1x2");
            padding = 1;
            paddingW = 2;
        }

        // _log("setConfig: font=" + fontW + "x" + fontH + ";scrollbar="
        // + scrollbarW + ";cols=" + config.getCols() + ";rows="
        // + config.getRows() + ";size=" + getWidth() + "x" + getHeight());

        setPs(config.getPs());
        setCols(config.getCols());
        setRows(config.getRows());
        setMaxBufferSize(config.getMaxBufferSize());
    }

    private void updateFontDimensions() {

        // Test element for font size
        DivElement test = Document.get().createDivElement();
        test.setAttribute("style", "position: absolute;");
        test.setInnerHTML("X");
        term.appendChild(test);

        fontW = test.getClientWidth();
        fontH = test.getClientHeight();
        if (fontW <= 0 || fontW > 100) {
            fontW = test.getOffsetWidth();
        }
        if (fontH <= 0 || fontH > 100) {
            fontH = test.getOffsetHeight();
        }
        if (fontW <= 0 || fontW > 100) {
            fontW = 1;
        }
        if (fontH <= 0 || fontH > 100) {
            fontH = 1;
        }
        term.removeChild(test);
    }

    // Debug instrumentation. TODO: Remove.
    // protected void _log(final String string) {
    // ApplicationConnection.getConsole().log(string);
    // }

    public TextConsoleConfig getConfig() {
        return config;
    }

    private boolean bufferEndsWithNewLine() {
        final Node last = buffer != null ? buffer.getLastChild() : null;
        // _log("last node: " + (last != null ? last.getNodeName() : "<null>"));
        return last != null && "br".equals(last.getNodeName().toLowerCase());
    }

    private Node createTextNode(final String text) {
        Element element = Document.get().createElement("span");
        element.setInnerHTML(text);
        return element;
    }

    private Node createBr() {
        return Document.get().createBRElement();
    }

    public void focusPrompt() {
        focusPrompt(-1);
    }

    public void focusPrompt(final int cursorPos) {
        input.focus();

        // Focus to end
        final String s = getInput();
        if (s != null && s.length() > 0) {
            setSelectionRange(input, s.length(), s.length());
        }
    }

    private native void setSelectionRange(Element input, int selectionStart,
            int selectionEnd)/*-{
                             if (input.setSelectionRange) {
                             input.focus();
                             input.setSelectionRange(selectionStart, selectionEnd);
                             }
                             else if (input.createTextRange) {
                             var range = input.createTextRange();
                             range.collapse(true);
                             range.moveEnd('character', selectionEnd);
                             range.moveStart('character', selectionStart);
                             range.select();
                             }
                             }-*/;

    private native int getScrollbarWidth()/*-{

                                          var i = $doc.createElement('p');
                                          i.style.width = '100%';
                                          i.style.height = '200px';
                                          var o = $doc.createElement('div');
                                          o.style.position = 'absolute';
                                          o.style.top = '0px';
                                          o.style.left = '0px';
                                          o.style.visibility = 'hidden';
                                          o.style.width = '200px';
                                          o.style.height = '150px';
                                          o.style.overflow = 'hidden';
                                          o.appendChild(i);
                                          $doc.body.appendChild(o);
                                          var w1 = i.offsetWidth;
                                          var h1 = i.offsetHeight;
                                          o.style.overflow = 'scroll';
                                          var w2 = i.offsetWidth;
                                          var h2 = i.offsetHeight;
                                          if (w1 == w2) w2 = o.clientWidth;
                                          if (h1 == h2) h2 = o.clientWidth;
                                          $doc.body.removeChild(o);
                                          return w1-w2;
                                          }-*/;

    public void newLine() {
        // _log("newline");
        buffer.appendChild(createBr());
        checkBufferLimit();
        reducePrompt(1);
    }

    protected void setPs(final String string) {
        cleanPs = Util.escapeHTML(string);
        cleanPs = cleanPs.replaceAll(" ", "&nbsp;");
    }

    public void prompt(final String inputText) {
        setPromtActive(true);
        scrollToEnd();
        ps.setInnerHTML(cleanPs);
        setInput(inputText);
    }

    public void scrollToEnd() {
        term.setScrollTop(BIG_NUMBER);
    }

    public void prompt() {
        prompt(null);
    }

    public void print(String string) {
        if (isPromptActive()) {
            setPromtActive(false);
            if (!bufferIsEmpty() && !bufferEndsWithNewLine()) {
                newLine();
                reducePrompt(-1);
            }
            string = getCurrentPromptContent() + string;
        }
        final boolean doWrap = config.isWrap();
        // _log("print original: '" + string + "' (" + doWrap + ")");
        String str = string.replaceAll("\t", tabs);

        // Continue to the last text node if available
        final Node last = getLastTextNode();
        int linesAdded = 0;
        if (last != null) {
            // _log("print append to old node: '" + last.getNodeValue() + "'");
            str = last.getNodeValue() + str;
            buffer.removeChild(last);
            linesAdded--;
        }

        // Split by the newlines anyway
        int s = 0, e = str.indexOf('\n');
        while (e >= s) {
            final String line = str.substring(s, e);
            linesAdded += appendLine(buffer, line, doWrap ? cols : -1);
            buffer.appendChild(createBr());
            s = e + 1;
            e = str.indexOf('\n', s);
        }

        // Print the remaining string
        if (s < str.length()) {
            linesAdded += appendLine(buffer, str.substring(s), doWrap ? cols
                    : -1);
        }

        reducePrompt(linesAdded);
    }

    private String getCurrentPromptContent() {
        return prompt.getInnerText() + getInput();
    }

    private void reducePrompt(final int rows) {
        int newRows = promptRows - rows;
        if (newRows < 1) {
            newRows = 1;
        }
        // _log("prompt reduced from " + promptRows + " to " + newRows);
        setPromptHeight(newRows);
    }

    private void setPromptHeight(final int rows) {
        final int min = 1;
        final int max = getRows();
        promptRows = rows < min ? min : (rows > max ? max : rows);
        final int newHeight = fontH * rows;
        // _log("Prompt height=" + newHeight);
        promptWrap.getStyle().setHeight(newHeight, Unit.PX);
    }

    /**
     * Split long text based on length.
     * 
     * @param parent
     * @param doWrap
     * @param str
     * @return
     */
    private int appendLine(final Node parent, String str, final int maxLine) {
        int linesAdded = 0;
        final boolean doWrap = maxLine > 0;
        if (!doWrap) {
            parent.appendChild(createTextNode(str));
            // _log("append: '" + str + "'");
            linesAdded++;
        } else {
            while (str.length() > maxLine) {
                final String piece = str.substring(0, maxLine);
                parent.appendChild(createTextNode(piece));
                parent.appendChild(createBr());
                linesAdded++;
                // _log("append: '" + piece + "'");
                str = str.substring(maxLine);
            }
            parent.appendChild(createTextNode(str));
            // _log("append rest: '" + str + "'");
            linesAdded++;
        }

        // make sore we don't exceed the maximum buffer size
        checkBufferLimit();

        return linesAdded;
    }

    private void checkBufferLimit() {

        // Buffer means only offscreen lines
        final int maxb = maxBufferSize + (rows - promptRows);
        while (getBufferSize() > maxb && buffer.hasChildNodes()) {
            buffer.removeChild(buffer.getFirstChild());
        }

    }

    private Node getLastTextNode() {
        if (buffer == null) {
            return null;
        }
        final Node l = buffer.getLastChild();
        if (l != null && l.getNodeType() == Node.TEXT_NODE) {
            return l;
        }
        return null;
    }

    public void println(final String string) {
        print(string + "\n");
    }

    @Override
    public void setHeight(final String height) {
        final int oldh = term.getClientHeight();
        super.setHeight(height);
        final int newh = term.getClientHeight();
        // _log("set height=" + height + " clientHeight="+oldh+" to "+newh);
        if (newh != oldh) {
            calculateRowsFromHeight();
        }
    }

    protected void calculateRowsFromHeight() {
        final int h = term.getClientHeight() - (2 * padding);
        rows = h / fontH;
        config.setRows(rows);

        // _log("calculateRowsFromHeight: font=" + fontW + "x" + fontH
        // + ";scrollbar=" + scrollbarW + ";cols=" + cols + ";rows="
        // + rows + ";size=" + getWidth() + "x" + getHeight());

    }

    protected void calculateHeightFromRows() {
        super.setHeight((rows * fontH) + "px");

        // _log("calculateHeightFromRows: font=" + fontW + "x" + fontH
        // + ";scrollbar=" + scrollbarW + ";cols=" + cols + ";rows="
        // + rows + ";size=" + getWidth() + "x" + getHeight());

    }

    protected void calculateColsFromWidth() {
        final int w = term.getClientWidth();
        cols = (w - 2 * paddingW) / fontW;
        config.setCols(cols);
        buffer.getStyle().setWidth((cols * fontW), Unit.PX);
        prompt.getStyle().setWidth((cols * fontW), Unit.PX);
        // _log("calculateColsFromWidth: font=" + fontW + "x" + fontH
        // + ";scrollbar=" + scrollbarW + ";cols=" + cols + ";rows="
        // + rows + ";size=" + getWidth() + "x" + getHeight());

    }

    protected void calculateWidthFromCols() {
        final int w = cols * fontW;
        super.setWidth((w + scrollbarW) + "px");
        buffer.getStyle().setWidth(w, Unit.PX);
        prompt.getStyle().setWidth(w, Unit.PX);

        // _log("calculateWidthFromCols: font=" + fontW + "x" + fontH
        // + ";scrollbar=" + scrollbarW + ";cols=" + cols + ";rows="
        // + rows + ";size=" + getWidth() + "x" + getHeight());
    }

    @Override
    public void setWidth(final String width) {
        final int oldw = term.getClientWidth();
        super.setWidth(width);
        final int neww = term.getClientWidth();
        // _log("set width=" + width + " clientWidth="+oldw+" to "+neww);
        if (neww != oldw) {
            calculateColsFromWidth();
        }
    }

    @Override
    public void setFocus(final boolean focused) {
        this.focused = focused;
        super.setFocus(focused);
        if (focused) {
            focusPrompt();
        }
    }

    public void setHandler(final TextConsoleHandler handler) {
        this.handler = handler;

    }

    public void setRows(final int rows) {
        if (rows > 0) {
            this.rows = rows;
            calculateHeightFromRows();
        } else {
            calculateRowsFromHeight();
        }
    }

    public int getRows() {
        return rows;
    }

    public void setCols(final int cols) {
        if (cols > 0) {
            this.cols = cols;
            calculateWidthFromCols();
        } else {
            calculateColsFromWidth();
        }
    }

    public int getCols() {
        return cols;
    }

    public void reset() {
        setPromtActive(false);
        clearBuffer();
        setPromptHeight(getRows());
        print(config.getGreeting());
        prompt();
    }

    public int getBufferSize() {
        return (buffer.getClientHeight() / fontH);
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    public void setMaxBufferSize(final int maxBuffer) {
        maxBufferSize = maxBuffer > 0 ? maxBuffer : 0;
        checkBufferLimit();
    }

    public void clearBuffer() {
        // Remove all children.
        while (buffer.hasChildNodes()) {
            buffer.removeChild(buffer.getFirstChild());
        }
    }

    public void formFeed() {
        for (int i = 0; i < promptRows; i++) {
            newLine();
        }
        setPromptHeight(getRows());
        scrollToEnd();

        checkBufferLimit();
    }

    protected void clearCommandHistory() {
        cmdHistory = new ArrayList<String>();
        cmdHistoryIndex = -1;
    }

    public String getHeight() {
        return (term.getClientHeight() - 2 * padding) + "px";
    }

    public String getWidth() {
        return (term.getClientWidth() + scrollbarW - 2 * paddingW) + "px";
    }

    protected void bell() {
        // Clear previous
        if (timer != null) {
            timer.cancel();
            timer = null;
            term.removeClassName("term-rev");
            input.removeClassName("term-rev");
        }
        // Add styles and start the timer
        input.addClassName("term-rev");
        term.addClassName("term-rev");
        timer = new Timer() {

            @Override
            public void run() {
                term.removeClassName("term-rev");
                input.removeClassName("term-rev");
            }
        };
        timer.schedule(150);
    }

    @Override
    protected void onUnload() {
        super.onUnload();

        if (clickHandler != null) {
            clickHandler.removeHandler();
        }
        if (focusHandler != null) {
            focusHandler.removeHandler();
        }
        if (keyHandler != null) {
            keyHandler.removeHandler();
        }
    }
}
