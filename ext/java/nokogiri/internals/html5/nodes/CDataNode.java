package nokogiri.internals.html5.nodes;

import org.w3c.dom.DOMException;
import nokogiri.internals.html5.internal.QuietAppendable;

/**
 * A Character Data node, to support CDATA sections.
 */
public class CDataNode extends TextNode implements org.w3c.dom.CDATASection{
    public CDataNode(String text) {
        super(text);
    }

    @Override public String getNodeName() { return "#cdata-section"; }
    @Override public String getNodeValue() { return text(); }
    @Override public short getNodeType() { return Node.CDATA_SECTION_NODE; }
    @Override public String getTextContent() throws DOMException { return text(); }
    @Override public String getData() throws DOMException { return text(); }
    @Override public int getLength() throws DOMException { return text().length(); }
    @Override public String substringData(int offset, int count) throws DOMException {
        return getData().substring(offset, offset + count);
    }

    @Override
    public String nodeName() {
        return "#cdata";
    }

    /**
     * Get the un-encoded, <b>non-normalized</b> text content of this CDataNode.
     * @return un-encoded, non-normalized text
     */
    @Override
    public String text() {
        return getWholeText();
    }

    @Override
    void outerHtmlHead(QuietAppendable accum, Document.OutputSettings out) {
        accum
            .append("<![CDATA[")
            .append(getWholeText())
            .append("]]>");
    }

    @Override
    public CDataNode clone() {
        return (CDataNode) super.clone();
    }
}
