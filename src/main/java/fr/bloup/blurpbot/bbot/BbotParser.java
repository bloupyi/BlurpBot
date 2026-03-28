package fr.bloup.blurpbot.bbot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fr.bloup.blurpbot.bbot.BbotModel.BotBlock;
import fr.bloup.blurpbot.bbot.BbotModel.ConditionBlock;
import fr.bloup.blurpbot.bbot.BbotModel.Document;
import fr.bloup.blurpbot.bbot.BbotModel.DynamicLocationAxis;
import fr.bloup.blurpbot.bbot.BbotModel.LeafBlock;
import fr.bloup.blurpbot.bbot.BbotModel.LetDirective;
import fr.bloup.blurpbot.bbot.BbotModel.NodeBlock;
import fr.bloup.blurpbot.bbot.BbotModel.RootBlock;
import fr.bloup.blurpbot.bbot.BbotModel.SetDirective;
import fr.bloup.blurpbot.bbot.BbotModel.ValueExpr;
import fr.bloup.blurpbot.bbot.BbotModel.WhenBlock;

public final class BbotParser {
    private final BbotTokenizer tokenizer;
    private BbotToken cur;

    private BbotParser(String source) {
        this.tokenizer = new BbotTokenizer(source);
    }

    public static Document parse(String source) throws BbotParseException {
        BbotParser p = new BbotParser(source);
        p.next();
        List<SetDirective> sets = new ArrayList<>();
        int version = 1;
        BotBlock bot = null;

        while (p.cur.type() != BbotTokenType.EOF) {
            if (p.isIdent("version")) {
                p.next();
                version = p.parseIntLiteral();
                continue;
            }
            if (p.isIdent("set")) {
                p.next();
                sets.add(p.parseSet());
                continue;
            }
            if (p.isIdent("bot")) {
                p.next();
                bot = p.parseBotBlock();
                continue;
            }
            throw new BbotParseException(p.cur.line(), "Unexpected token: " + p.describeCur());
        }

        if (bot == null) {
            throw new BbotParseException(1, "Missing bot { ... } block");
        }
        return new Document(version, List.copyOf(sets), bot);
    }

    private String describeCur() {
        return cur.type() + (cur.text().isEmpty() ? "" : " '" + cur.text() + "'");
    }

    private void next() throws BbotParseException {
        cur = tokenizer.nextToken();
    }

    private int parseIntLiteral() throws BbotParseException {
        if (cur.type() != BbotTokenType.NUMBER) {
            throw new BbotParseException(cur.line(), "Expected number");
        }
        int v = (int) Double.parseDouble(cur.text());
        next();
        return v;
    }

    private SetDirective parseSet() throws BbotParseException {
        String key = expectIdentToken();
        String value;
        if (cur.type() == BbotTokenType.NUMBER || cur.type() == BbotTokenType.STRING) {
            value = cur.text();
            next();
        } else {
            throw new BbotParseException(cur.line(), "Expected value after set key");
        }
        return new SetDirective(key, value);
    }

    private BotBlock parseBotBlock() throws BbotParseException {
        String name = expectName();
        expect(BbotTokenType.LBRACE);
        RootBlock root = parseRoot();
        expect(BbotTokenType.RBRACE);
        return new BotBlock(name, root);
    }

    private String expectName() throws BbotParseException {
        if (cur.type() == BbotTokenType.STRING) {
            String n = cur.text();
            next();
            return n;
        }
        return expectIdentToken();
    }

    private RootBlock parseRoot() throws BbotParseException {
        expectIdent("root");
        expectIdent("priority_selector");
        expect(BbotTokenType.LBRACE);
        List<NodeBlock> nodes = new ArrayList<>();
        while (!isType(BbotTokenType.RBRACE)) {
            expectIdent("node");
            String id = expectIdentToken();
            expect(BbotTokenType.LBRACE);
            nodes.add(parseNodeBody(id));
        }
        next();
        return new RootBlock(List.copyOf(nodes));
    }

    private NodeBlock parseNodeBody(String id) throws BbotParseException {
        ValueExpr priority = null;
        List<LetDirective> lets = new ArrayList<>();
        WhenBlock when = null;
        List<LeafBlock> leaves = new ArrayList<>();

        while (!isType(BbotTokenType.RBRACE)) {
            if (isIdent("let")) {
                next();
                String letName = expectIdentToken();
                expect(BbotTokenType.EQUALS);
                ValueExpr expr = parseValueExpr();
                lets.add(new LetDirective(letName, expr));
            } else if (isIdent("priority")) {
                next();
                expect(BbotTokenType.COLON);
                priority = parseValueExpr();
            } else if (isIdent("when")) {
                next();
                expect(BbotTokenType.COLON);
                when = parseWhen();
            } else if (isIdent("leaf") || isIdent("run")) {
                next();
                leaves.add(parseLeafBlock());
            } else {
                throw new BbotParseException(cur.line(), "Unknown field in node: " + describeCur());
            }
        }
        next(); // closing node brace

        if (priority == null) {
            throw new BbotParseException(cur.line(), "Node '" + id + "' missing priority");
        }
        if (leaves.isEmpty()) {
            throw new BbotParseException(cur.line(), "Node '" + id + "' missing leaf");
        }
        return new NodeBlock(id, lets, priority, when, leaves);
    }

    private WhenBlock parseWhen() throws BbotParseException {
        expectIdent("sequence");
        expect(BbotTokenType.LBRACE);
        List<ConditionBlock> conds = new ArrayList<>();
        while (!isType(BbotTokenType.RBRACE)) {
            expectIdent("condition");
            String cname = expectIdentToken();
            expect(BbotTokenType.LBRACE);
            Map<String, ValueExpr> args = parseArgBlock();
            conds.add(new ConditionBlock(cname, args));
        }
        next();
        return new WhenBlock(List.copyOf(conds));
    }

    private LeafBlock parseLeafBlock() throws BbotParseException {
        String name = expectIdentToken();
        expect(BbotTokenType.LBRACE);
        Map<String, ValueExpr> args = parseArgBlock();
        return new LeafBlock(name, args);
    }

    private Map<String, ValueExpr> parseArgBlock() throws BbotParseException {
        Map<String, ValueExpr> m = new LinkedHashMap<>();
        while (!isType(BbotTokenType.RBRACE)) {
            String k = expectIdentToken();
            expect(BbotTokenType.COLON);
            m.put(k, parseValueExpr());
        }
        next();
        return m;
    }

    private ValueExpr parseValueExpr() throws BbotParseException {
        return switch (cur.type()) {
            case NUMBER -> {
                double v = Double.parseDouble(cur.text());
                next();
                yield new ValueExpr.NumberVal(v);
            }
            case STRING -> {
                String t = cur.text();
                next();
                yield new ValueExpr.StringVal(t);
            }
            case DOLLAR -> {
                next();
                String k = expectIdentToken();
                yield new ValueExpr.SettingRef(k);
            }
            case AT -> {
                next();
                String base = expectIdentToken();
                if (isType(BbotTokenType.DOT)) {
                    expect(BbotTokenType.DOT);
                    String prop1 = expectIdentToken();
                    expect(BbotTokenType.DOT);
                    String prop2 = expectIdentToken();
                    String n1 = prop1.toLowerCase(Locale.ROOT);
                    String n2 = prop2.toLowerCase(Locale.ROOT);
                    if (!"location".equals(n1)) {
                        throw new BbotParseException(cur.line(), "Unsupported property after @" + base + ": " + prop1);
                    }
                    DynamicLocationAxis axis = switch (n2) {
                        case "x" -> DynamicLocationAxis.X;
                        case "y" -> DynamicLocationAxis.Y;
                        case "z" -> DynamicLocationAxis.Z;
                        case "yaw" -> DynamicLocationAxis.YAW;
                        case "pitch" -> DynamicLocationAxis.PITCH;
                        default -> throw new BbotParseException(cur.line(), "location.<axis> must be x|y|z|yaw|pitch, got: " + prop2);
                    };
                    yield new ValueExpr.DynamicLocationComponent(base, axis);
                }
                yield new ValueExpr.DynamicVarRef(base);
            }
            case IDENT -> {
                String t = cur.text();
                next();
                yield new ValueExpr.StringVal(t);
            }
            default -> throw new BbotParseException(cur.line(), "Expected value, got " + describeCur());
        };
    }

    private void expect(BbotTokenType t) throws BbotParseException {
        if (cur.type() != t) {
            throw new BbotParseException(cur.line(), "Expected " + t + ", got " + describeCur());
        }
        next();
    }

    private void expectIdent(String word) throws BbotParseException {
        if (!isIdent(word)) {
            throw new BbotParseException(cur.line(), "Expected '" + word + "', got " + describeCur());
        }
        next();
    }

    private String expectIdentToken() throws BbotParseException {
        if (cur.type() != BbotTokenType.IDENT) {
            throw new BbotParseException(cur.line(), "Expected identifier, got " + describeCur());
        }
        String t = cur.text();
        next();
        return t;
    }

    private boolean isIdent(String word) {
        return cur.type() == BbotTokenType.IDENT && word.equals(cur.text());
    }

    private boolean isType(BbotTokenType t) {
        return cur.type() == t;
    }
}
