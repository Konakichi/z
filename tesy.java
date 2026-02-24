import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class XmlIdrefValueRemover {

    /**
     * XMLストリームを読み込み、IDREF属性を持つタグのテキスト値を削除して出力します。
     *
     * @param in  入力XMLストリーム
     * @param out 出力XMLストリーム
     */
    public static void removeIdrefValues(InputStream in, OutputStream out) throws XMLStreamException {
        // ファクトリの初期化
        XMLInputFactory inFactory = XMLInputFactory.newInstance();
        // セキュリティ対策（XXE脆弱性防止）のため外部エンティティを無効化
        inFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        inFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        XMLOutputFactory outFactory = XMLOutputFactory.newInstance();

        XMLEventReader reader = inFactory.createXMLEventReader(in, StandardCharsets.UTF_8.name());
        XMLEventWriter writer = outFactory.createXMLEventWriter(out, StandardCharsets.UTF_8.name());

        // 現在のタグ階層がIDREF属性を持っているかを管理するスタック
        Deque<Boolean> idrefStack = new ArrayDeque<>();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();

            switch (event.getEventType()) {
                case XMLStreamConstants.START_ELEMENT:
                    StartElement startElement = event.asStartElement();
                    boolean hasIdref = false;
                    
                    // 属性を走査して "IDREF" (大文字小文字問わず) があるか確認
                    Iterator<Attribute> attributes = startElement.getAttributes();
                    while (attributes.hasNext()) {
                        Attribute attr = attributes.next();
                        if ("idref".equalsIgnoreCase(attr.getName().getLocalPart())) {
                            hasIdref = true;
                            break;
                        }
                    }
                    
                    // 現在のタグの状態をスタックに積む
                    idrefStack.push(hasIdref);
                    writer.add(event); // 開始タグ自体はそのまま出力
                    break;

                case XMLStreamConstants.END_ELEMENT:
                    // タグが閉じたのでスタックから取り出す
                    idrefStack.pop();
                    writer.add(event);
                    break;

                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    // 現在のタグがIDREFを持っていなければ、テキストを出力する
                    // 持っている場合は writer.add() をスキップするため、値が削除される
                    if (idrefStack.isEmpty() || !idrefStack.peek()) {
                        writer.add(event);
                    }
                    break;

                default:
                    // コメントや処理命令などはそのまま出力
                    writer.add(event);
                    break;
            }
        }

        // リソースのフラッシュとクローズ
        writer.flush();
        writer.close();
        reader.close();
    }

    // 実行テスト用のメインメソッド
    public static void main(String[] args) {
        String xmlData = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<root>\n" +
            "    <item IDREF=\"ref-1\">ここの値は削除されます</item>\n" +
            "    <item id=\"2\">ここの値は保持されます</item>\n" +
            "    <parent IDREF=\"ref-2\">\n" +
            "        削除されるテキスト\n" +
            "        <child>子要素の構造は維持されます</child>\n" +
            "    </parent>\n" +
            "</root>";

        try (ByteArrayInputStream in = new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            removeIdrefValues(in, out);
            System.out.println(out.toString(StandardCharsets.UTF_8.name()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
