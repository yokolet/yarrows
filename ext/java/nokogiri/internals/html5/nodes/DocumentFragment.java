package nokogiri.internals.html5.nodes;

import org.w3c.dom.NamedNodeMap;

public class DocumentFragment extends Element implements org.w3c.dom.DocumentFragment {
    public DocumentFragment() {
        super("#document-fragment");
    }

    @Override public String getNodeName() { return "#document-fragment"; }
    @Override public String getNodeValue() { return null; }
    @Override public short getNodeType() { return Node.DOCUMENT_FRAGMENT_NODE; }
    @Override public Node getParentNode() { return null; }
    @Override public NamedNodeMap getAttributes() { return null; }
    @Override public org.w3c.dom.Document getOwnerDocument() { return ownerDocument; }
    @Override public String getNamespaceURI() { return null; }
    @Override public String getPrefix() { return null; }
    @Override public String getLocalName() { return null; }
    @Override public boolean hasAttributes() { return false; }
    @Override public String getBaseURI() { return baseUri().trim().equals("") ? null : baseUri().trim(); }
    @Override public String lookupPrefix(String namespaceURI) { return null; }
    @Override public boolean isDefaultNamespace(String namespaceURI) { return false; }
}
