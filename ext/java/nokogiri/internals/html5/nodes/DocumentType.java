package nokogiri.internals.html5.nodes;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;

import nokogiri.internals.html5.internal.QuietAppendable;
import nokogiri.internals.html5.internal.StringUtil;
import nokogiri.internals.html5.helper.Validate;
import nokogiri.internals.html5.nodes.Document.OutputSettings.Syntax;

/**
 * A {@code <!DOCTYPE>} node.
 */
public class DocumentType extends LeafNode implements org.w3c.dom.DocumentType {
    // todo needs a bit of a chunky cleanup. this level of detail isn't needed
    public static final String PUBLIC_KEY = "PUBLIC";
    public static final String SYSTEM_KEY = "SYSTEM";
    private static final String NameKey = "name";
    private static final String PubSysKey = "pubSysKey"; // PUBLIC or SYSTEM
    private static final String PublicId = "publicId";
    private static final String SystemId = "systemId";
    private static final String InternalSubsetKey = Attributes.internalKey("doctypeInternalSubset");

    /**
     * Create a new doctype element.
     * @param name the doctype's name
     * @param publicId the doctype's public ID
     * @param systemId the doctype's system ID
     */
    public DocumentType(String name, String publicId, String systemId) {
        super(name);
        Validate.notNull(publicId);
        Validate.notNull(systemId);
        attributes()
            .add(NameKey, name)
            .add(PublicId, publicId)
            .add(SystemId, systemId);
        updatePubSyskey();
    }

    // org.w3c.dom.DocumentType methods
    @Override public String getNodeName() { return name(); }
    @Override public String getNodeValue() throws DOMException { return null; }
    @Override public short getNodeType() { return DOCUMENT_TYPE_NODE; }
    @Override public NamedNodeMap getAttributes() { return EMPTY_MAP; }
    @Override public org.w3c.dom.Document getOwnerDocument() { return null; }
    @Override public String getTextContent() throws DOMException { return null; }
    @Override public String getName() { return name(); }
    @Override public NamedNodeMap getEntities() { return EMPTY_MAP; }
    @Override public NamedNodeMap getNotations() { return EMPTY_MAP; }
    @Override public String getPublicId() { return publicId(); }
    @Override public String getSystemId() { return systemId(); }
    @Override public String getInternalSubset() { return attributes().get(InternalSubsetKey); }

    public void setPubSysKey(@Nullable String value) {
        if (value != null)
            attr(PubSysKey, value);
    }

    /**
     Sets the raw XML internal subset for serialization.
     @param value the internal subset contents
     */
    public void setInternalSubset(String value) {
        attributes().put(InternalSubsetKey, value);
    }

    private void updatePubSyskey() {
        if (has(PublicId)) {
            attributes().add(PubSysKey, PUBLIC_KEY);
        } else if (has(SystemId))
            attributes().add(PubSysKey, SYSTEM_KEY);
    }

    /**
     * Get this doctype's name (when set, or empty string)
     * @return doctype name
     */
    public String name() {
        return attr(NameKey);
    }

    /**
     * Get this doctype's Public ID (when set, or empty string)
     * @return doctype Public ID
     */
    public String publicId() {
        return attr(PublicId);
    }

    /**
     * Get this doctype's System ID (when set, or empty string)
     * @return doctype System ID
     */
    public String systemId() {
        return attr(SystemId);
    }

    @Override
    public String nodeName() {
        return "#doctype";
    }

    @Override
    void outerHtmlHead(QuietAppendable accum, Document.OutputSettings out) {
        if (out.syntax() == Syntax.html && !has(PublicId) && !has(SystemId)) {
            // looks like a html5 doctype, go lowercase for aesthetics
            accum.append("<!doctype");
        } else {
            accum.append("<!DOCTYPE");
        }
        if (has(NameKey))
            accum.append(" ").append(attr(NameKey));
        if (has(PubSysKey))
            accum.append(" ").append(attr(PubSysKey));
        if (has(PublicId))
            accum.append(" \"").append(attr(PublicId)).append('"');
        if (has(SystemId))
            accum.append(" \"").append(attr(SystemId)).append('"');
        accum.append('>');
    }

    private boolean has(final String attribute) {
        return !StringUtil.isBlank(attr(attribute));
    }
}
