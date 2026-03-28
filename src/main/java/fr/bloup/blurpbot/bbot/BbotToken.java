package fr.bloup.blurpbot.bbot;

public record BbotToken(BbotTokenType type, String text, int line) {
    public static BbotToken eof(int line) {
        return new BbotToken(BbotTokenType.EOF, "", line);
    }
}
