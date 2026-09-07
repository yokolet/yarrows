package nokogiri;

import nokogiri.internals.Html5ParserContext;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyString;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.*;

import static nokogiri.internals.NokogiriHelpers.getNokogiriClass;

/**
 * Class for Nokogiri::HTML5::Document.
 *
 * @author Yoko Harada <yokolet@gmail.com>
 */
@JRubyClass(name = "Nokogiri::HTML5::Document", parent = "Nokogiri::HTML4::Document")
public class Html5Document extends Html4Document
{
  private static final long serialVersionUID = 1L;

  private static final String DEFAULT_CONTENT_TYPE = "html";
  private static final String DEFAULT_PUBLIC_ID = null;
  private static final String DEFAULT_SYSTEM_ID = null;

  private String parsed_encoding = null;

  public Html5Document(Ruby ruby, RubyClass klazz)
  {
    super(ruby, klazz);
  }

  public Html5Document(Ruby runtime, Document document)
  {
    this(runtime, getNokogiriClass(runtime, "Nokogiri::HTML5::Document"), document);
  }

  public Html5Document(Ruby ruby, RubyClass klazz, Document doc)
  {
    super(ruby, klazz, doc);
  }

  @JRubyMethod(name = "new", meta = true, rest = true)
  public static IRubyObject
  rbNew(ThreadContext context, IRubyObject klazz, IRubyObject[] args)
  {
    final Ruby runtime = context.runtime;
    Html5Document html5Document;
    try {
      Document docNode = createNewDocument(runtime);
      html5Document = (Html5Document) NokogiriService.HTML5_DOCUMENT_ALLOCATOR.allocate(runtime, (RubyClass) klazz);
      html5Document.setDocumentNode(context.runtime, docNode);
    } catch (Exception ex) {
      throw asRuntimeError(runtime, "couldn't create document: ", ex);
    }

    Helpers.invoke(context, html5Document, "initialize", args);

    return html5Document;
  }

  public IRubyObject
  getInternalSubset(ThreadContext context)
  {
    IRubyObject internalSubset = super.getInternalSubset(context);

    // html documents are expected to have a default internal subset
    // the default values are the same ones used when the following
    // feature is turned on
    // "http://cyberneko.org/html/features/insert-doctype"
    // the reason we don't turn it on, is because it overrides the document's
    // declared doctype declaration.

    if (internalSubset.isNil()) {
      internalSubset = XmlDtd.newEmpty(context.getRuntime(),
                                       getDocument(),
                                       context.getRuntime().newString(DEFAULT_CONTENT_TYPE),
                                       context.getRuntime().newString(DEFAULT_PUBLIC_ID),
                                       context.getRuntime().newString(DEFAULT_SYSTEM_ID));
      setInternalSubset(internalSubset);
    }

    return internalSubset;
  }

  @Override
  void
  init(Ruby runtime, Document document)
  {
    //stabilizeTextContent(document);  // guess jsoup doesn't need stabilize text and attrs
    document.normalize();
    setInstanceVariable("@decorators", runtime.getNil());
//    if (document.getDocumentElement() != null) {
//      stabilizeAttrs(document.getDocumentElement());
//    }
  }

  private static void
  stabilizeAttrs(Node node)
  {
    if (node.hasAttributes()) {
      NamedNodeMap nodeMap = node.getAttributes();
      for (int i = 0; i < nodeMap.getLength(); i++) {
        Node n = nodeMap.item(i);
        if (n instanceof Attr) {
          stabilizeAttr((Attr) n);
        }
      }
    }
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      stabilizeAttrs(children.item(i));
    }
  }

  public void
  setParsedEncoding(String encoding)
  {
    parsed_encoding = encoding;
  }

  public String
  getParsedEncoding()
  {
    return parsed_encoding;
  }

  /*
  args[0] : input
  args[1] : url
  args[2] : encoding
  args[3] : ParseOptions::DEFAULT_HTML
  args[4] : options in Hash
  */
  @JRubyMethod(meta = true, required = 5)
  public static IRubyObject
  parse_io(ThreadContext context, IRubyObject klass, IRubyObject[] args)
  {
    Html5ParserContext ctx = new Html5ParserContext(context.runtime, args[2], args[3], args[4]);
    ctx.setIOInputSource(context, args[0], args[1]);
    return ctx.parse(context, (RubyClass) klass, args[1]);
  }

  /*
  args[0] : input
  args[1] : url
  args[2] : encoding
  args[3] : ParseOptions::DEFAULT_HTML
  args[4] : options in Hash
  */
  @JRubyMethod(meta = true, required = 5)
  public static IRubyObject
  parse_memory(ThreadContext context, IRubyObject klass, IRubyObject[] args)
  {
    Html5ParserContext ctx = new Html5ParserContext(context.runtime, args[2], args[3], args[4]);
    ctx.setStringInputSource(context, args[0], args[1]);
    return ctx.parse(context, (RubyClass) klass, args[1]);
  }

  /*
  args[0] : input
  args[1] : context node || document
  args[2] : encoding
  args[3] : ParseOptions::DEFAULT_HTML
  args[4] : options in Hash
 */
  @JRubyMethod(meta = true, required = 5)
  public static IRubyObject
  fragment_from_io(ThreadContext context, IRubyObject klass, IRubyObject[] args)
  {
    Html5ParserContext ctx = new Html5ParserContext(context.runtime, args[2], args[3], args[4]);
    ctx.setIOInputSource(context, args[0], RubyString.newEmptyString(context.runtime));
    return ctx.parse_fragment(context, (RubyClass) klass, args[1]);
  }

  /*
  args[0] : input
  args[1] : context node || document
  args[2] : encoding
  args[3] : ParseOptions::DEFAULT_HTML
  args[4] : options in Hash
   */
  @JRubyMethod(meta = true, required = 5)
  public static IRubyObject
  fragment_from_memory(ThreadContext context, IRubyObject klass, IRubyObject[] args)
  {
    Html5ParserContext ctx = new Html5ParserContext(context.runtime, args[2], args[3], args[4]);
    ctx.setStringInputSource(context, args[0], RubyString.newEmptyString(context.runtime));
    return ctx.parse_fragment(context, (RubyClass) klass, args[1]);
  }

  @JRubyMethod(rest = true, required = 1, optional = 1)
  public IRubyObject html_standard_serialize(ThreadContext context, IRubyObject[] args)
  {
    XmlDocument xmlDocument = document(context.runtime);
    if (xmlDocument.getDocument() instanceof nokogiri.internals.html5.nodes.Document)
    {
      nokogiri.internals.html5.nodes.Document internalDocument =
        (nokogiri.internals.html5.nodes.Document) xmlDocument.getDocument();
      return RubyString.newString(context.runtime, internalDocument.outerHtml());
    }
    return RubyString.newString(context.runtime, "");
  }
}
