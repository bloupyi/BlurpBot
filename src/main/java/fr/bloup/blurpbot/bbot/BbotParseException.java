package fr.bloup.blurpbot.bbot;

public class BbotParseException extends Exception {
    private final int line;

    public BbotParseException(int line, String message) {
        super(message);
        this.line = line;
    }

    public int line() {
        return line;
    }

    @Override
    public String getMessage() {
        return "line " + line + ": " + super.getMessage();
    }
}
