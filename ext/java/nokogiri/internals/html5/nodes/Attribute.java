package nokogiri.internals.html5.nodes;

import nokogiri.internals.html5.helper.Validate;
import nokogiri.internals.html5.internal.Normalizer;
import nokogiri.internals.html5.internal.QuietAppendable;
import nokogiri.internals.html5.internal.SharedConstants;
import nokogiri.internals.html5.internal.StringUtil;
import nokogiri.internals.html5.nodes.Document.OutputSettings.Syntax;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 A single key + value attribute. (Only used for presentation.)
 */
public class Attribute implements Cloneable, Attr  {
    private static final String[] booleanAttributes = {
            "allowfullscreen", "async", "autofocus", "checked", "compact", "declare", "default", "defer", "disabled",
            "formnovalidate", "hidden", "inert", "ismap", "itemscope", "multiple", "muted", "nohref", "noresize",
            "noshade", "novalidate", "nowrap", "open", "readonly", "required", "reversed", "seamless", "selected",
            "sortable", "truespeed", "typemustmatch"
    };

    private String key;
    @Nullable private String val;
    @Nullable Attributes parent; // used to update the holding Attributes when the key / value is changed via this interface
    private HashMap<String, Object> userdata;

    /**
     * Create a new attribute from unencoded (raw) key and value.
     * @param key attribute key; case is preserved.
     * @param value attribute value (may be null)
     * @see #createFromEncoded
     */
    public Attribute(String key, @Nullable String value) {
        this(key, value, null);
    }

    /**
     * Create a new attribute from unencoded (raw) key and value.
     * @param key attribute key; case is preserved.
     * @param val attribute value (may be null)
     * @param parent the containing Attributes (this Attribute is not automatically added to said Attributes)
     * @see #createFromEncoded*/
    public Attribute(String key, @Nullable String val, @Nullable Attributes parent) {
        Validate.notNull(key);
        key = key.trim();
        Validate.notEmpty(key); // trimming could potentially make empty, so validate here
        this.key = key;
        this.val = val;
        this.parent = parent;
    }

    // org.w3c.dom.TypeInfo implementation
    public static final TypeInfo EMPTY_TYPE_INFO = new TypeInfo() {
        @Override public boolean isDerivedFrom(String typeNamespaceArg, String typeNameArg, int derivationMethod) { return false; }
        @Override public String getTypeName() { return null; }
        @Override public String getTypeNamespace() { return "http://www.w3.org/1999/xhtml"; }
    };

    // org.w3c.dom.Node and org.w3c.dom.Attr methods
    @Override public String getNodeName() { return getKey(); };
    @Override public String getNodeValue() throws DOMException { return getValue(); }
    @Override public void setNodeValue(String value) throws DOMException { setValue(value); }
    @Override public short getNodeType() { return Node.ATTRIBUTE_NODE; };
    @Override public Node getParentNode() { return null; }
    @Override public NodeList getChildNodes() { return nokogiri.internals.html5.nodes.Node.EMPTY_LIST; }
    @Override public Node getFirstChild() { return null; };
    @Override public Node getLastChild() { return null; };
    @Override public Node getPreviousSibling() { return null; }
    @Override public Node getNextSibling() { return null; }
    @Override public NamedNodeMap getAttributes() { return null; };
    @Override public org.w3c.dom.Document getOwnerDocument() { return parent != null ? parent.ownerElement.getOwnerDocument() : null;  }
    @Override public Node insertBefore(org.w3c.dom.Node newChild, org.w3c.dom.Node refChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public Node replaceChild(org.w3c.dom.Node newChild, org.w3c.dom.Node oldChild) throws DOMException {
        // should call replaceChildInner(Node out, Node in)
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public Node removeChild(org.w3c.dom.Node oldChild) throws DOMException {
        // should call removeChildInner(Node out)
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public Node appendChild(org.w3c.dom.Node newChild) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "Will be implemented later");
    }
    @Override public boolean hasChildNodes() { return false; }
    @Override public Node cloneNode(boolean deep) { return clone(); }
    @Override public void normalize() {
        throw new RuntimeException("Will be implemented later");
    }
    @Override public boolean isSupported(String feature, String version) { return false; }
    @Override public String getNamespaceURI() {
        String namespaceURI = namespace();
        return (namespaceURI == null || namespaceURI.isEmpty()) ? null : namespaceURI;
    }
    @Override public String getPrefix() {
        String prefix = prefix();
        return (prefix == null || prefix.isEmpty()) ? null : prefix;
    }
    @Override public void setPrefix(String prefix) throws DOMException {
        throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, "HTML doesn't have a prefix");
    }
    @Override public String getLocalName() {
        String namespaceURI = namespace();
        return (namespaceURI == null || namespaceURI.isEmpty()) ? null : localName();
    }
    @Override public boolean hasAttributes() { return false; }
    @Override public String getBaseURI() {
        if (parent == null || parent.ownerElement == null) { return null; }
        String baseURI = parent.ownerElement.baseUri();
        return (baseURI == null || baseURI.isEmpty()) ? null : baseURI;
    }
    @Override public short compareDocumentPosition(org.w3c.dom.Node other) throws DOMException {
        if (isSameNode(other)) { return 0; }
        if (getOwnerDocument() != other.getOwnerDocument()) { return Node.DOCUMENT_POSITION_DISCONNECTED; }
        if (other instanceof org.w3c.dom.Attr && this.getOwnerElement() == ((Attr) other).getOwnerElement()) {
            int thisIdx = parent.indexOfKey(this.getNodeName());
            int otherIdx = parent.indexOfKey(other.getNodeName());
            if ( thisIdx < otherIdx) { return Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC | Node.DOCUMENT_POSITION_FOLLOWING; }
            else { return Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC | Node.DOCUMENT_POSITION_PRECEDING; }
        }

        List<org.w3c.dom.Node> ancestors = new ArrayList<>();

        // test if this node is an ancestor of the other
        org.w3c.dom.Node node = other instanceof Attr ? ((Attr) other).getOwnerElement() : other;
        ancestors.add(node);
        org.w3c.dom.Node parent = node.getParentNode();
        while (parent != null) {
            if (parent == this.getOwnerElement()) { return Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_FOLLOWING; }
            ancestors.add(parent);
            parent = parent.getParentNode();
        }

        // test if the other is an ancestor of this node while checking a common ancestor
        parent = getOwnerElement();
        int count = 1;
        while (parent != null) {
            if (parent == node) { return Node.DOCUMENT_POSITION_CONTAINS | Node.DOCUMENT_POSITION_PRECEDING; }
            if (ancestors.contains(parent)) {
                // found the common ancestor
                int pos = ancestors.indexOf(parent);
                if (pos >= count) { return Node.DOCUMENT_POSITION_FOLLOWING; }
                else { return Node.DOCUMENT_POSITION_PRECEDING; }
            }
            parent = parent.getParentNode();
            count++;
        }
        return Node.DOCUMENT_POSITION_DISCONNECTED;
    }

    @Override public String getTextContent() throws DOMException { return getValue(); }
    @Override public void setTextContent(String textContent) throws DOMException { /* does nothing */ }
    @Override public boolean isSameNode(org.w3c.dom.Node other) {
        return this == other;
    }
    @Override public String lookupPrefix(String namespaceURI) { return namespaceURI.equals(namespace()) ? prefix() : null; }
    @Override public boolean isDefaultNamespace(String namespaceURI) {
        return !getOwnerElement().noNamespace && namespaceURI.equals("http://www.w3.org/1999/xhtml");
    }
    @Override public String lookupNamespaceURI(String prefix) {
        if (prefix == null)  { return getOwnerElement().noNamespace ? null : "http://www.w3.org/1999/xhtml"; }
        return !getOwnerElement().noNamespace && prefix.equals(prefix()) ? namespace() : null;
    }
    @Override public boolean isEqualNode(org.w3c.dom.Node arg) {
        if (arg == null) { return false; }
        if (isSameNode(arg)) { return true; }
        if (!getClass().isInstance(arg)) { return false; }
        if (areSame(getNodeName(), arg.getNodeName()) &&
                areSame(getLocalName(), arg.getLocalName()) &&
                areSame(getNamespaceURI(), arg.getNamespaceURI()) &&
                areSame(getPrefix(), arg.getPrefix()) &&
                areSame(getNodeValue(), arg.getNodeValue())) {
            return true;
        }
        return false;
    }
    private static boolean areSame(final String a, final String b) {
        if (a == null) { return b == null; }
        return a.equals(b);
    }

    @Override public Object getFeature(String feature, String version) { return null; }
    @Override public Object setUserData(String key, Object data, UserDataHandler handler) {
        if (userdata == null) { userdata = new HashMap<String, Object>(); }
        return userdata.put(key, data);
    }
    @Override public Object getUserData(String key) {
        if (userdata == null) { return null; }
        return userdata.get(key);
    }

    @Override public String getName() { return getKey(); }
    @Override public boolean getSpecified() { return !getValue().isEmpty(); }
    // @Override public String getValue() { return val; } // exactly the same implementation exists
    // @Override void setValue(String value) throws DOMException { val = value; } // exactly the same implementation exists
    @Override public Element getOwnerElement() { return parent != null ? parent.ownerElement : null; }
    @Override public TypeInfo getSchemaTypeInfo() { return EMPTY_TYPE_INFO; }
    @Override public boolean isId() { return getKey().equalsIgnoreCase("id"); }

    /**
     Get the attribute's key (aka name).
     @return the attribute key
     */
    public String getKey() {
        return key;
    }

    /**
     Set the attribute key; case is preserved.
     @param key the new key; must not be null
     */
    public void setKey(String key) {
        Validate.notNull(key);
        key = key.trim();
        Validate.notEmpty(key); // trimming could potentially make empty, so validate here
        if (parent != null) {
            int i = parent.indexOfKey(this.key);
            if (i != Attributes.NotFound) {
                String oldKey = parent.keys[i];
                parent.keys[i] = key;

                // if tracking source positions, update the key in the range map
                Map<String, Range.AttributeRange> ranges = parent.getRanges();
                if (ranges != null) {
                    Range.AttributeRange range = ranges.remove(oldKey);
                    ranges.put(key, range);
                }
            }
        }
        this.key = key;
    }

    /**
     Get the attribute value. Will return an empty string if the value is not set.
     @return the attribute value
     */
    @Override
    public String getValue() {
        return Attributes.checkNotNull(val);
    }

    /**
     * Check if this Attribute has a value. Set boolean attributes have no value.
     * @return if this is a boolean attribute / attribute without a value
     */
    public boolean hasDeclaredValue() {
        return val != null;
    }

    /**
     Set the attribute value.
     @param val the new attribute value; may be null (to set an enabled boolean attribute)
     @return the previous value (if was null; an empty string)
     */
    @Override public void setValue(@Nullable String val) {
        String oldVal = this.val;
        if (parent != null) {
            int i = parent.indexOfKey(this.key);
            if (i != Attributes.NotFound) {
                oldVal = parent.get(this.key); // trust the container more
                parent.vals[i] = val;
            }
        }
        this.val = val;
        //return Attributes.checkNotNull(oldVal);
    }

    /**
     Get this attribute's key prefix, if it has one; else the empty string.
     <p>For example, the attribute {@code og:title} has prefix {@code og}, and local {@code title}.</p>

     @return the tag's prefix
     @since 1.20.1
     */
    public String prefix() {
        int pos = key.indexOf(':');
        if (pos == -1) return "";
        else return key.substring(0, pos);
    }

    /**
     Get this attribute's local name. The local name is the name without the prefix (if any).
     <p>For example, the attribute key {@code og:title} has local name {@code title}.</p>

     @return the tag's local name
     @since 1.20.1
     */
    public String localName() {
        int pos = key.indexOf(':');
        if (pos == -1) return key;
        else return key.substring(pos + 1);
    }

    /**
     Get this attribute's namespace URI, if the attribute was prefixed with a defined namespace name. Otherwise, returns
     the empty string. These will only be defined if using the XML parser.
     @return the tag's namespace URI, or empty string if not defined
     @since 1.20.1
     */
    public String namespace() {
        // set as el.attributes.userData(SharedConstants.XmlnsAttr + prefix, ns)
        if (parent != null) {
            String ns = (String) parent.userData(SharedConstants.XmlnsAttr + prefix());
            if (ns != null)
                return ns;
        }
        return "";
    }

    /**
     Get the HTML representation of this attribute; e.g. {@code href="index.html"}.
     @return HTML
     */
    public String html() {
        StringBuilder sb = StringUtil.borrowBuilder();
        html(QuietAppendable.wrap(sb), new Document.OutputSettings());
        return StringUtil.releaseBuilder(sb);
    }

    /**
     Get the source ranges (start to end positions) in the original input source from which this attribute's <b>name</b>
     and <b>value</b> were parsed.
     <p>Position tracking must be enabled prior to parsing the content.</p>
     @return the ranges for the attribute's name and value, or {@code untracked} if the attribute does not exist or its range
     was not tracked.
     @see nokogiri.internals.html5.parser.Parser#setTrackPosition(boolean)
     @see Attributes#sourceRange(String)
     //@see Node#sourceRange()
     @see Element#endSourceRange()
     @since 1.17.1
     */
    public Range.AttributeRange sourceRange() {
        if (parent == null) return Range.AttributeRange.UntrackedAttr;
        return parent.sourceRange(key);
    }

    void html(QuietAppendable accum, Document.OutputSettings out) {
        html(key, val, accum, out);
    }

    static void html(String key, @Nullable String val, QuietAppendable accum, Document.OutputSettings out) {
        key = getValidKey(key, out.syntax());
        if (key == null) return; // can't write it :(
        htmlNoValidate(key, val, accum, out);
    }

    /** @deprecated internal method and will be removed in a future version */
    @Deprecated
    protected void html(Appendable accum, Document.OutputSettings out) throws IOException {
        html(key, val, accum, out);
    }

    /** @deprecated internal method and will be removed in a future version */
    @Deprecated
    protected static void html(String key, @Nullable String val, Appendable accum, Document.OutputSettings out) throws IOException {
        html(key, val, QuietAppendable.wrap(accum), out);
    }

    static void htmlNoValidate(String key, @Nullable String val, QuietAppendable accum, Document.OutputSettings out) {
        // structured like this so that Attributes can check we can write first, so it can add whitespace correctly
        accum.append(key);
        if (!shouldCollapseAttribute(key, val, out)) {
            accum.append("=\"");
            Entities.escape(accum, Attributes.checkNotNull(val), out, Entities.ForAttribute); // preserves whitespace
            accum.append('"');
        }
    }

    private static final Pattern xmlKeyReplace = Pattern.compile("[^-a-zA-Z0-9_:.]+");
    private static final Pattern htmlKeyReplace = Pattern.compile("[\\x00-\\x1f\\x7f-\\x9f \"'/=]+");
    /**
     * Get a valid attribute key for the given syntax. If the key is not valid, it will be coerced into a valid key.
     * @param key the original attribute key
     * @param syntax HTML or XML
     * @return the original key if it's valid; a key with invalid characters replaced with "_" otherwise; or null if a valid key could not be created.
     */
    @Nullable public static String getValidKey(String key, Syntax syntax) {
        if (syntax == Syntax.xml && !isValidXmlKey(key)) {
            key = xmlKeyReplace.matcher(key).replaceAll("_");
            return isValidXmlKey(key) ? key : null; // null if could not be coerced
        }
        else if (syntax == Syntax.html && !isValidHtmlKey(key)) {
            key = htmlKeyReplace.matcher(key).replaceAll("_");
            return isValidHtmlKey(key) ? key : null; // null if could not be coerced
        }
        return key;
    }

    // perf critical in html() so using manual scan vs regex:
    // note that we aren't using anything in supplemental space, so OK to iter charAt
    private static boolean isValidXmlKey(String key) {
        // =~ [a-zA-Z_:][-a-zA-Z0-9_:.]*
        final int length = key.length();
        if (length == 0) return false;
        char c = key.charAt(0);
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == ':'))
            return false;
        for (int i = 1; i < length; i++) {
            c = key.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == ':' || c == '.'))
                return false;
        }
        return true;
    }

    private static boolean isValidHtmlKey(String key) {
        // =~ [\x00-\x1f\x7f-\x9f "'/=]+
        final int length = key.length();
        if (length == 0) return false;
        for (int i = 0; i < length; i++) {
            char c = key.charAt(i);
            if ((c <= 0x1f) || (c >= 0x7f && c <= 0x9f) || c == ' ' || c == '"' || c == '\'' || c == '/' || c == '=')
                return false;
        }
        return true;
    }

    /**
     Get the string representation of this attribute, implemented as {@link #html()}.
     @return string
     */
    @Override
    public String toString() {
        return html();
    }

    /**
     * Create a new Attribute from an unencoded key and a HTML attribute encoded value.
     * @param unencodedKey assumes the key is not encoded, as can be only run of simple \w chars.
     * @param encodedValue HTML attribute encoded value
     * @return attribute
     */
    public static Attribute createFromEncoded(String unencodedKey, String encodedValue) {
        String value = Entities.unescape(encodedValue, true);
        return new Attribute(unencodedKey, value, null); // parent will get set when Put
    }

    protected boolean isDataAttribute() {
        return isDataAttribute(key);
    }

    protected static boolean isDataAttribute(String key) {
        return key.startsWith(Attributes.dataPrefix) && key.length() > Attributes.dataPrefix.length();
    }

    /**
     * Collapsible if it's a boolean attribute and value is empty or same as name
     * 
     * @param out output settings
     * @return  Returns whether collapsible or not
     * @deprecated internal method and will be removed in a future version
     */
    @Deprecated
    protected final boolean shouldCollapseAttribute(Document.OutputSettings out) {
        return shouldCollapseAttribute(key, val, out);
    }

    // collapse unknown foo=null, known checked=null, checked="", checked=checked; write out others
    protected static boolean shouldCollapseAttribute(final String key, @Nullable final String val, final Document.OutputSettings out) {
        return (out.syntax() == Syntax.html &&
                (val == null || (val.isEmpty() || val.equalsIgnoreCase(key)) && Attribute.isBooleanAttribute(key)));
    }

    /**
     * Checks if this attribute name is defined as a boolean attribute in HTML5
     */
    public static boolean isBooleanAttribute(final String key) {
        return Arrays.binarySearch(booleanAttributes, Normalizer.lowerCase(key)) >= 0;
    }

    @Override
    public boolean equals(@Nullable Object o) { // note parent not considered
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attribute attribute = (Attribute) o;
        return Objects.equals(key, attribute.key) && Objects.equals(val, attribute.val);
    }

    @Override
    public int hashCode() { // note parent not considered
        return Objects.hash(key, val);
    }

    @Override
    public Attribute clone() {
        try {
            return (Attribute) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
