package nokogiri.internals.html5.nodes;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import nokogiri.internals.html5.helper.Validate;
import nokogiri.internals.html5.internal.QuietAppendable;

/**
 A node that does not hold any children. E.g.: {@link TextNode}, {@link DataNode}, {@link Comment}.
 */
public abstract class LeafNode extends Node implements CharacterData {
    Object value; // either a string value, or an attribute map (in the rare case multiple attributes are set)

    public LeafNode() {
        value = "";
    }

    protected LeafNode(String coreValue) {
        Validate.notNull(coreValue);
        value = coreValue;
    }

    // org.w3c.dom.CharacterData methods
    @Override public NodeList getChildNodes() {  return EMPTY_LIST; }
    @Override public Node getFirstChild() { return null; }
    @Override public Node getLastChild() { return null; }
    @Override public NamedNodeMap getAttributes() { return null; }
    @Override public org.w3c.dom.Document getOwnerDocument() { return ownerDocument(); }
    @Override public Node appendChild(org.w3c.dom.Node newChild) throws DOMException {
      throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "CharacterData node is not allowed to have children");
    }
    @Override public boolean hasChildNodes() { return false; }
    @Override public String getNamespaceURI() { return null; }
    @Override public String getPrefix() { return null; }
    @Override public String getLocalName() { return null; }
    @Override public String getBaseURI() { return baseUri().equals("") ? null : baseUri().trim(); }
    @Override public String lookupPrefix(String namespaceURI) { return null; }
    @Override public boolean isDefaultNamespace(String namespaceURI) { return false; }
    @Override public String getData() throws DOMException { return null; }
    @Override public void setData(String data) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public int getLength() { return 0; }
    @Override public String substringData(int offset, int count) throws DOMException { return null; }
    @Override public void appendData(String data) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public void insertData(int offest, String data) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public void deleteData(int offest, int count) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public void replaceData(int offest, int count, String arg) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }

    @Override
    public boolean hasAttributes() {
        return value instanceof Attributes;
    }

    @Override
    public final Attributes attributes() {
        ensureAttributes();
        return (Attributes) value;
    }

    /** Expands this leaf's compact value into attribute storage when needed. */
    final void ensureAttributes() {
        if (!hasAttributes()) {
            String coreValue = coreValue();
            Range.Spans rangeSpans = spans();
            Attributes attributes = newAttributes(coreValue);
            value = attributes;
            if (rangeSpans != null)
                attributes.putSpans(rangeSpans);
        }
    }

    /** Expands this leaf's compact value into attribute storage. */
    Attributes newAttributes(String coreValue) {
        Attributes attributes = new Attributes();
        attributes.put(nodeName(), coreValue);
        return attributes;
    }

    String coreValue() {
        if (value instanceof Attributes)   return ((Attributes) value).get(nodeName());
        if (value instanceof TrackedValue) return ((TrackedValue) value).coreValue;
        return (String) value;
    }

    @Override @Nullable
    public Element parent() {
        return parentNode;
    }

    @Override
    public String nodeValue() {
        return coreValue();
    }

    void coreValue(String value) {
        if (this.value instanceof Attributes)
            ((Attributes) this.value).put(nodeName(), value);
        else if (this.value instanceof TrackedValue)
            ((TrackedValue) this.value).coreValue = value;
        else
            this.value = value;
    }

    @Override
    public String attr(String key) {
        if (!hasAttributes())
            return nodeName().equals(key) ? coreValue() : EmptyString;
        return super.attr(key);
    }

    @Override
    public Node attr(String key, String value) {
        if (!hasAttributes() && key.equals(nodeName())) {
            this.value = value;
        } else {
            ensureAttributes();
            super.attr(key, value);
        }
        return this;
    }

    @Override
    public boolean hasAttr(String key) {
        ensureAttributes();
        return super.hasAttr(key);
    }

    @Override
    public Node removeAttr(String key) {
        ensureAttributes();
        return super.removeAttr(key);
    }

    @Override
    public String absUrl(String key) {
        ensureAttributes();
        return super.absUrl(key);
    }

    @Override
    public String baseUri() {
        return parentNode != null ? parentNode.baseUri() : "";
    }

    @Override
    protected void doSetBaseUri(String baseUri) {
        // noop
    }

    @Override
    public int childNodeSize() {
        return 0;
    }

    @Override
    public Node empty() {
        return this;
    }

    @Override
    protected List<Node> ensureChildNodes() {
        return EmptyNodes;
    }

    @Override
    void outerHtmlTail(QuietAppendable accum, Document.OutputSettings out) {}

    @Override
    protected LeafNode doClone(Node parent) {
        LeafNode clone = (LeafNode) super.doClone(parent);

        // Object value could be plain string, tracked string, or attributes - need to clone.
        if (hasAttributes())
            clone.value = ((Attributes) value).clone();
        else if (value instanceof TrackedValue)
            clone.value = ((TrackedValue) value).copy();

        return clone;
    }

    @Override Range.@Nullable Spans spans() {
        if (value instanceof TrackedValue)
            return ((TrackedValue) value).spans;
        return super.spans();
    }

    @Override Range.Spans ensureSpans() {
        // Leaf nodes normally hold just their core string. When source ranges are tracked, keep the string plus spans
        // in a small wrapper so leaf nodes do not expand to Attributes just for parser metadata. If attributes are
        // later requested, ensureAttributes() moves these same spans into the Attributes object.
        if (value instanceof TrackedValue)
            return ((TrackedValue) value).spans;
        if (value instanceof Attributes)
            return ((Attributes) value).ensureSpans();

        TrackedValue trackedValue = new TrackedValue((String) value);
        value = trackedValue;
        return trackedValue.spans;
    }

    /**
     Holds a compact leaf value plus ranges without expanding to Attributes.
     */
    private static final class TrackedValue {
        String coreValue;
        final Range.Spans spans;

        /**
         Creates a tracked leaf value around the core text.
         */
        TrackedValue(String coreValue) {
            this(coreValue, new Range.Spans());
        }

        /**
         Creates a tracked leaf value around copied range spans.
         */
        TrackedValue(String coreValue, Range.Spans spans) {
            this.coreValue = coreValue;
            this.spans = spans;
        }

        /**
         Returns a copy so cloned leaf nodes can mutate range spans independently.
         */
        TrackedValue copy() {
            return new TrackedValue(coreValue, spans.copy());
        }
    }
}
