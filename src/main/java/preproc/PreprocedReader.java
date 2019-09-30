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
import java.util.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;

/**
 * 事前処理の実装例（関数版）
 */
public class PreprocedReader extends PipedReader {

    private static final String contents =
            "The quick brown fox jumps over the lazy dog.\n"
          + "色は匂へど散りぬるを我が世誰そ常ならむ有為の奥山今日越えて浅き夢見じ酔ひもせず。\n"
          + "アイウエオカキクケコ ABC abc 0123456789";

    private static final Path pathIn = Paths.get(".", "data.txt");
    private static final Charset csIn = StandardCharsets.UTF_8;

    public static void main( String[] args ) throws IOException{

        sample03();
        //sample04();
    }

    /**
     * 3.関数版の使用例
     */
    static void sample03() throws IOException{

        InputStream is0 = Files.newInputStream(pathIn);
        InputStreamReader reader1 = new InputStreamReader(is0, csIn);
        Reader reader2 = new PreprocedReader(reader1, TRIM_COL(0, 5), DUMP_STDOUT, ESCAPE);
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
     * 4.関数版の使用例（事前処理の実行順を変えてみる）
     */
    static void sample04() throws IOException{

        InputStream is0 = Files.newInputStream(pathIn);
        InputStreamReader reader1 = new InputStreamReader(is0, csIn);
        Reader reader2 = new PreprocedReader(reader1, ESCAPE, DUMP_STDOUT, TRIM_COL(0, 5));
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
     * ※ByteArrayInputStream版
     */
    static void sample04b() throws IOException{

        InputStream is0 = new ByteArrayInputStream(contents.getBytes());
        InputStreamReader reader1 = new InputStreamReader(is0, csIn);
        Reader reader2 = new PreprocedReader(reader1, ESCAPE, DUMP_STDOUT, TRIM_COL(0, 5));
        BufferedReader reader = new BufferedReader(reader2);

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

    private final Reader in;

    /**
     * （合成された）事前処理。
     * 初期値は単位元。
     */
    private Preprocess preprocs = Preprocess.identity();

    public PreprocedReader(Reader in) throws IOException {
        this.in = in;
        doPreproc();
    }

    public PreprocedReader(Reader in, Preprocess...preprocs) throws IOException {
        this.in = in;
        this.preprocs = Preprocess.compose(preprocs);
        doPreproc();
    }

    private void doPreproc() throws IOException {
        PipedWriter pipedOut = new PipedWriter();
        this.connect(pipedOut);

        BufferedReader reader = new BufferedReader(in);
        BufferedWriter writer = new BufferedWriter(pipedOut);
        try (reader; writer;) {
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                pipedOut.write(preprocs.apply(lineNo, line));
                pipedOut.write('\n');
            }
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    // -------------------------------------------------- //
    // 事前処理の実装

    /**
     * 事前処理インターフェイス
     */
    @FunctionalInterface
    public static interface Preprocess {

        /**
         * 事前処理
         *
         * @param lineNo 対象行番号
         * @param line 対象行文字列
         * @return 事前処理結果
         */
        public String apply(int lineNo, String line);

        // ----- ----- //

        /**
         * 事前処理の合成
         *
         * @param next 合成する事前処理
         * @return 合成後の事前処理
         */
        default Preprocess compose(Preprocess next) {
            return (int n, String v) -> next.apply(n, this.apply(n, v));
        }

//        default Preprocess compose(Preprocess prev) {
//            return (int n, String v) -> this.apply(n, prev.apply(n, v));
//        }

        /**
         * 単位元
         *
         * @return
         */
        public static Preprocess identity() {
            return (lineNo, line) -> line;
        }

        /**
         * 複数の事前処理を合成するユーティリティ関数
         *
         * @param preprocs 合成対象の事前処理
         * @return
         */
        static Preprocess compose(final Preprocess... preprocs) {
            return Stream.of(preprocs).reduce((preproc, next) -> preproc.compose(next)).orElse(identity());
        }
    }

    //

    /**
     * 《事前処理》エスケープ処理を行う
     */
    private static class PreprocEscape implements Preprocess {
        @Override
        public String apply(int lineNo, String line) {
            return org.apache.commons.text.StringEscapeUtils.escapeJava(line);
        }
    }
    public static final Preprocess ESCAPE = new PreprocEscape(); // 糖衣構文

    /**
     * 《事前処理》指定された列位置でファイルをトリムする
     */
    private static class PreprocTrimCol implements Preprocess {

        private final int from;
        private final int to;

        public PreprocTrimCol(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String apply(int lineNo, String line) {
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
    public static final Preprocess TRIM_COL(int from, int to) {  // 糖衣構文
        return new PreprocTrimCol(from, to);
    }

    /**
     * 《事前処理》行操作は行わず、行内容を標準出力へ出力する
     */
    private static class PreprocDumpStdout implements Preprocess {
        @Override
        public String apply(int lineNo, String line) {
            System.out.println("[DUMP]"+line);

            return line;
        }
    }
    public static final Preprocess DUMP_STDOUT = new PreprocDumpStdout();  // 糖衣構文

    /**
     * 《事前処理》アンエスケープ処理を行う
     */
    private static class PreprocUnescape implements Preprocess {

        public PreprocUnescape() {
        }

        @Override
        public String apply(int lineNo, String line) {
            return StringEscapeUtils.unescapeJava(line);
        }
    }
    public static final Preprocess UNESCAPE = new PreprocUnescape();

}
