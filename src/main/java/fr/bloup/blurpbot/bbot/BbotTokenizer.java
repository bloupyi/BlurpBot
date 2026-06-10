package fr.bloup.blurpbot.bbot;

public final class BbotTokenizer {
    private final String s;
    private int pos;
    private int line = 1;

    public BbotTokenizer(String s) {
        this.s = s;
    }

    public int line() {
        return line;
    }

    public BbotToken nextToken() throws BbotParseException {
        skipWsAndComments();
        if (pos >= s.length()) {
            return BbotToken.eof(line);
        }
        char c = s.charAt(pos);
        int lineStart = line;
        switch (c) {
            case '{' -> {
                pos++;
                return new BbotToken(BbotTokenType.LBRACE, "{", lineStart);
            }
            case '}' -> {
                pos++;
                return new BbotToken(BbotTokenType.RBRACE, "}", lineStart);
            }
            case '(' -> {
                pos++;
                return new BbotToken(BbotTokenType.LPAREN, "(", lineStart);
            }
            case ')' -> {
                pos++;
                return new BbotToken(BbotTokenType.RPAREN, ")", lineStart);
            }
            case ':' -> {
                pos++;
                return new BbotToken(BbotTokenType.COLON, ":", lineStart);
            }
            case '$' -> {
                pos++;
                return new BbotToken(BbotTokenType.DOLLAR, "$", lineStart);
            }
            case '@' -> {
                pos++;
                return new BbotToken(BbotTokenType.AT, "@", lineStart);
            }
            case '=' -> {
                pos++;
                return new BbotToken(BbotTokenType.EQUALS, "=", lineStart);
            }
            case '.' -> {
                pos++;
                return new BbotToken(BbotTokenType.DOT, ".", lineStart);
            }
            case '+' -> {
                pos++;
                return new BbotToken(BbotTokenType.PLUS, "+", lineStart);
            }
            case '*' -> {
                pos++;
                return new BbotToken(BbotTokenType.STAR, "*", lineStart);
            }
            case '/' -> {
                pos++;
                return new BbotToken(BbotTokenType.SLASH, "/", lineStart);
            }
            case '-' -> {
                if (pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))) {
                    return readNumber(lineStart);
                }
                pos++;
                return new BbotToken(BbotTokenType.MINUS, "-", lineStart);
            }
            case '"' -> {
                return readString(lineStart);
            }
            default -> {
                if (Character.isDigit(c)) {
                    return readNumber(lineStart);
                }
                if (isIdentStart(c)) {
                    return readIdent(lineStart);
                }
                throw new BbotParseException(lineStart, "Unexpected character: '" + c + "'");
            }
        }
    }

    private void skipWsAndComments() {
        while (pos < s.length()) {
            char c = s.charAt(pos);
            switch (c) {
                case ' ', '\t', '\r' -> {
                    pos++;
                }
                case '\n' -> {
                    pos++;
                    line++;
                }
                case '#' -> {
                    while (pos < s.length() && s.charAt(pos) != '\n') {
                        pos++;
                    }
                }
                default -> {
                    return;
                }
            }
        }
    }

    private BbotToken readString(int lineStart) throws BbotParseException {
        pos++; // opening "
        StringBuilder sb = new StringBuilder();
        while (pos < s.length()) {
            char c = s.charAt(pos++);
            if (c == '"') {
                return new BbotToken(BbotTokenType.STRING, sb.toString(), lineStart);
            }
            if (c == '\\' && pos < s.length()) {
                char e = s.charAt(pos++);
                sb.append(switch (e) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '\\', '"' -> e;
                    default -> e;
                });
            } else {
                sb.append(c);
            }
        }
        throw new BbotParseException(lineStart, "Unclosed string literal");
    }

    private BbotToken readNumber(int lineStart) throws BbotParseException {
        int start = pos;
        if (s.charAt(pos) == '-') {
            pos++;
        }
        if (pos >= s.length() || !Character.isDigit(s.charAt(pos))) {
            throw new BbotParseException(lineStart, "Invalid number");
        }
        while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
            pos++;
        }
        if (pos < s.length() && s.charAt(pos) == '.') {
            pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
        }
        String text = s.substring(start, pos);
        return new BbotToken(BbotTokenType.NUMBER, text, lineStart);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private BbotToken readIdent(int lineStart) {
        int start = pos;
        pos++;
        while (pos < s.length() && isIdentPart(s.charAt(pos))) {
            pos++;
        }
        String text = s.substring(start, pos);
        return new BbotToken(BbotTokenType.IDENT, text, lineStart);
    }
}
