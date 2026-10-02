package dk.itu.datasys;

import dk.itu.datasys.sql.SqlLexer;
import dk.itu.datasys.sql.SqlParser.ScriptContext;
import dk.itu.datasys.sql.SqlParser.StatementContext;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class SqlParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlParser.class);

    public List<Statement> parse(String sqlText) {
        long startTime = System.currentTimeMillis();

        try {
            SqlLexer lexer = new SqlLexer(CharStreams.fromString(sqlText));
            lexer.removeErrorListeners();
            lexer.addErrorListener(THROWING_LISTENER);

            dk.itu.datasys.sql.SqlParser antlrParser =
                    new dk.itu.datasys.sql.SqlParser(new CommonTokenStream(lexer));
            antlrParser.removeErrorListeners();
            antlrParser.addErrorListener(THROWING_LISTENER);

            ScriptContext scriptContext = antlrParser.script();

            SqlAstBuilder builder = new SqlAstBuilder();
            List<Statement> statements = new ArrayList<>();
            for (StatementContext statementContext : scriptContext.statement()) {
                statements.add((Statement) builder.visit(statementContext));
            }

            long durationMs = System.currentTimeMillis() - startTime;
            LOGGER.debug("statements={} durationMs={}", statements.size(), durationMs);

            return statements;

        } catch (SqlParseException e) {
            long durationMs = System.currentTimeMillis() - startTime;
            LOGGER.error("failed line={} col={} durationMs={}", e.line(), e.column(), durationMs);
            throw e;
        }
    }

    private static final BaseErrorListener THROWING_LISTENER =
            new BaseErrorListener() {
                @Override
                public void syntaxError(
                        Recognizer<?, ?> recognizer,
                        Object offendingSymbol,
                        int line,
                        int charPositionInLine,
                        String msg,
                        RecognitionException e) {
                    throw new SqlParseException(msg, line, charPositionInLine);
                }
            };
}