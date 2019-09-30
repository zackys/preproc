package preproc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 事前処理の実装例（Decoratorパターン版）
 */
public class PreprocedReaderOld extends PipedReader {

    private static final String contents =
            "The quick brown fox jumps over the lazy dog.\n"
          + "色は匂へど散りぬるを我が世誰そ常ならむ有為の奥山今日越えて浅き夢見じ酔ひもせず。\n"
          + "アイウエオカキクケコ ABC abc 0123456789";

    private final static Path pathIn = Paths.get(".", "data.txt");
    private final static Charset csIn = StandardCharsets.UTF_8;

    public static void main( String[] args ) throws IOException{

        //sample00();
        //sample01();
        sample02();
    }

    /**
     * 0.Decoratorの使用例（事前処理追加前）
     */
    static void sample00() throws IOException{

        InputStream is0 = Files.newInputStream(pathIn);
        InputStreamReader reader1 = new InputStreamReader(is0, csIn);
        BufferedReader reader = new BufferedReader(reader1);

        //BufferedReader reader = Files.newBufferedReader(pathIn, csIn); // 普通はこちらを使用
        System.out.println("--本処理--");
        try (reader) {
            reader.lines().forEach(line->{
                // 本処理を記述
                System.out.println(line);
            });
        }
    }

    /**
     * 1.Decoratorの使用例（事前処理を追加）
     */
    static final void sample01() throws IOException {

        InputStream is0 = Files.newInputStream(pathIn);
        InputStreamReader reader1 = new InputStreamReader(is0, csIn);
        Reader reader2 = new PreprocedReaderOld(reader1); // 事前処理を追加
        BufferedReader reader = new BufferedReader(reader2);

        System.out.println("--本処理--");
        try (reader) {
            reader.lines().forEach(line->{
                // 本処理を記述
                System.out.println(line);
            });
        }
    }

    /**
     * 2.Decoratorの使用例（複数の事前処理を追加）
     */
    static final void sample02() throws IOException {

        InputStream is0 = Files.newInputStream(pathIn);
        InputStreamReader reader1 = new InputStreamReader(is0, csIn);
        Reader reader2 = TRIM_COL(reader1, 0, 5);  // 事前処理を追加
        Reader reader3 = DUMP_STDOUT(reader2);     // 事前処理その２を追加
        Reader reader4 = ESCAPE(reader3);          // 事前処理その３を追加
        BufferedReader reader = new BufferedReader(reader4);

        System.out.println("--本処理--");
        try (reader) {
            reader.lines().forEach(line->{
                // 本処理を記述
                System.out.println(line);
            });
        }
    }

    /**
     * ※ByteArrayInputStream版
     */
    static final void sample02b() throws IOException {
        InputStream is0 = new ByteArrayInputStream(contents.getBytes());
        InputStreamReader reader1 = new InputStreamReader(is0, csIn);
        Reader reader2 = TRIM_COL(reader1, 0, 5);  // 事前処理を追加
        Reader reader3 = DUMP_STDOUT(reader2);     // 事前処理その２を追加
        Reader reader4 = ESCAPE(reader3);          // 事前処理その３を追加
        BufferedReader reader = new BufferedReader(reader4);

        System.out.println("--本処理--");
        try (reader) {
            reader.lines().forEach(line->{
                // 本処理を記述
                System.out.println(line);
            });
        }
    }

    // ====================================================================== //
    // 事前処理実行クラスの実装

    protected Reader in;

    public PreprocedReaderOld(Reader in) throws IOException {
        this.in = in;
        doPreproc();
    }

    protected PreprocedReaderOld() {
    }

    protected void doPreproc() throws IOException {
        PipedWriter pipedOut = new PipedWriter();
        this.connect(pipedOut);

        BufferedReader reader = new BufferedReader(in);
        BufferedWriter writer = new BufferedWriter(pipedOut);
        try (reader; writer;) {
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                pipedOut.write(preprocess(lineNo, line));
                pipedOut.write('\n');
                lineNo++;
            }
        }
    }

    protected String preprocess(int lineNo, String line) {
        return "!事前処理! " + line;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    // -------------------------------------------------- //
    // 事前処理の実装

    /**
     * 《事前処理》エスケープ処理を行う
     */
    private static class PreprocEscape extends PreprocedReaderOld {
        public PreprocEscape(Reader in) throws IOException {
            this.in = in;

            doPreproc();
        }

        @Override
        public String preprocess(int lineNo, String line) {
            return org.apache.commons.text.StringEscapeUtils.escapeJava(line);
        }
    }
    public static PreprocedReaderOld ESCAPE(Reader in) throws IOException {
        return new PreprocEscape(in);
    }

    /**
     * 《事前処理》指定された列位置でファイルをトリムする
     */
    private static class PreprocTrimCol extends PreprocedReaderOld {

        private final int from;
        private final int to;;

        public PreprocTrimCol(Reader in, int from, int to) throws IOException {
            this.in= in;
            this.from = from;
            this.to = to;

            doPreproc();
        }

        @Override
        public String preprocess(int lineNo, String line) {
            final int len = line.length();
            if (len < to) {
                return line.substring(from);
            } else if (to <= from) {
                return "";
            } else {
                return line.substring(from, to);
            }
        }
    }
    public static PreprocedReaderOld TRIM_COL(Reader in, int from, int to) throws IOException {
        return new PreprocTrimCol(in, from, to);
    }

    /**
     * 《事前処理》行操作は行わず、行内容を標準出力へ出力する
     */
    private static class PreprocDumpStdout extends PreprocedReaderOld {
        public PreprocDumpStdout(Reader in) throws IOException {
            this.in = in;

            doPreproc();
        }

        @Override
        public String preprocess(int lineNo, String line) {
            System.out.println("[DUMP]"+line);

            return line;
        }
    }
    public static PreprocedReaderOld DUMP_STDOUT(Reader in) throws IOException {
        return new PreprocDumpStdout(in);
    }

}
