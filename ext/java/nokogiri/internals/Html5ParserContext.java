package nokogiri.internals;

import nokogiri.*;
import nokogiri.internals.html5.nodes.Document;
import nokogiri.internals.html5.nodes.Element;
import nokogiri.internals.html5.nodes.Node;
import nokogiri.internals.html5.parser.ParseError;
import nokogiri.internals.html5.parser.ParseErrorList;
import nokogiri.internals.html5.parser.Parser;
import org.jruby.*;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.DocumentFragment;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static nokogiri.internals.NokogiriHelpers.stringOrNil;

/**
 * Parser for Html5Document. This class actually parses Html5Document using own lib based on jsoup.
 *
 * @author Yoko Harada <yokolet@gmail.com>
 */
public class Html5ParserContext extends ParserContext
{
  private static final long serialVersionUID = 1L;
  protected ParserContext.Options options;
  protected ParserContext.Settings settings;
  protected transient NokogiriErrorHandler errorHandler;
  protected transient Parser parser;
  protected transient IRubyObject ruby_encoding;

  public Html5ParserContext(Ruby runtime, IRubyObject options, IRubyObject settings)
  {
    this(runtime, runtime.getNil(), options, settings);
  }

  public Html5ParserContext(Ruby runtime, IRubyObject encoding, IRubyObject options,  IRubyObject settings)
  {
    super(runtime);
    this.options = new ParserContext.Options(RubyFixnum.fix2long(options));
    this.settings = new ParserContext.Settings(settings.toJava(RubyHash.class));
    java_encoding = NokogiriHelpers.getValidEncodingOrNull(encoding);
    ruby_encoding = encoding;
    initErrorHandler(runtime);
    initParser(runtime);
  }

  protected void
  initErrorHandler(Ruby runtime)
  {
    if (options.recover) {
      errorHandler = new NokogiriNonStrictErrorHandler(runtime, options.noError, options.noWarning);
    } else {
      errorHandler = new NokogiriStrictErrorHandler(runtime, options.noError, options.noWarning);
    }
  }

  protected void
  initParser(Ruby runtime)
  {
    parser = Parser.htmlParser();
    parser.setTrackPosition(true);
    parser.setTrackErrors((int)settings.maxErrors);
    parser.setMaxDepth((int)settings.maxTreeDepth);
  }

  @Override
  public void
  setEncoding(String encoding)
  {
    super.setEncoding(encoding);
  }

  public void
  addErrorsIfNecessary(ThreadContext context, XmlDocument doc)
  {
    doc.setInstanceVariable("@errors", mapErrors(context, errorHandler, parser, doc.getDocument().getBaseURI()));
  }

  public static RubyArray<?>
  mapErrors(ThreadContext context, NokogiriErrorHandler errorHandler, Parser parser, String baseURI)
  {
    final Ruby runtime = context.runtime;
    final List<RubyException> errors = errorHandler.getErrors();
    appendParseErrors(runtime, parser, errors, baseURI);
    final IRubyObject[] errorsAry = new IRubyObject[errors.size()];
    for (int i = 0; i < errors.size(); i++) {
      errorsAry[i] = errors.get(i);
    }
    return runtime.newArrayNoCopy(errorsAry);
  }

  private static void appendParseErrors(Ruby runtime, Parser parser, List<RubyException> errors, String baseURI) {
    ParseErrorList errorList = parser.getErrors();
    for (ParseError error : errorList) {
      XmlSyntaxError rubyError = XmlSyntaxError.createXMLSyntaxError(runtime, new RuntimeException(error.getErrorMessage()));
      rubyError.setInstanceVariable("@file", NokogiriHelpers.stringOrBlank(runtime, baseURI));
      errors.add(rubyError);
    }
  }

  public XmlDocument
  getDocumentWithErrorsOrRaiseException(ThreadContext context, RubyClass klazz, Exception ex)
  {
    if (options.recover) {
      XmlDocument xmlDocument = getInterruptedOrNewXmlDocument(context, klazz);
      this.addErrorsIfNecessary(context, xmlDocument);
      XmlSyntaxError xmlSyntaxError = XmlSyntaxError.createXMLSyntaxError(context.runtime);
      xmlSyntaxError.setException(ex);
      ((RubyArray) xmlDocument.getInstanceVariable("@errors")).append(xmlSyntaxError);
      return xmlDocument;
    } else {
      XmlSyntaxError xmlSyntaxError = XmlSyntaxError.createXMLSyntaxError(context.runtime);
      xmlSyntaxError.setException(ex);
      throw xmlSyntaxError.toThrowable();
    }
  }

  private XmlDocument
  getInterruptedOrNewXmlDocument(ThreadContext context, RubyClass klass)
  {
    Document document = new nokogiri.internals.html5.nodes.Document("");
    XmlDocument xmlDocument = new XmlDocument(context.runtime, klass, document);
    xmlDocument.setEncoding(ruby_encoding);
    return xmlDocument;
  }

  /**
   * Must call setInputSource() before this method.
   */
  public XmlDocument
  parse(ThreadContext context, RubyClass klass, IRubyObject url)
  {
    XmlDocument xmlDoc;
    try {
      Document doc = do_parse();
      doc.outputSettings().charset(StandardCharsets.UTF_8);
      xmlDoc = wrapDocument(context, klass, doc);
      xmlDoc.setUrl(url);
      if (!url.isNil()) { xmlDoc.setInstanceVariable("@url", url); }
      addErrorsIfNecessary(context, xmlDoc);
      return xmlDoc;
    } catch (SAXException e) {
      return getDocumentWithErrorsOrRaiseException(context, klass, e);
    } catch (IOException e) {
      return getDocumentWithErrorsOrRaiseException(context, klass, e);
    }
  }

  protected Document
  do_parse() throws SAXException, IOException
  {
    Reader reader = new InputStreamReader(getInputSource().getByteStream(), java_encoding);
    String url = getInputSource().getSystemId();
    return parser.parseInput(reader, url != null ? url : "");
  }

  protected XmlDocument
  wrapDocument(ThreadContext context, RubyClass klass, Document document)
  {
    Html5Document htmlDocument = new Html5Document(context.runtime, klass, document);
    htmlDocument.setDocumentNode(context.runtime, document);
    Helpers.invoke(context, htmlDocument, "initialize");

    if (ruby_encoding.isNil()) {
      // ruby_encoding might have detected by Html4Document::EncodingReader
      if (detected_encoding != null && !detected_encoding.isNil()) {
        ruby_encoding = detected_encoding;
      } else {
        // no encoding given & no encoding detected, then try to get it
        String charset = document.getInputEncoding();
        ruby_encoding = stringOrNil(context.runtime, charset);
      }
    }
    htmlDocument.setParsedEncoding(java_encoding);
    return htmlDocument;
  }

  public XmlDocumentFragment
  parse_fragment(ThreadContext context, RubyClass klass, IRubyObject fragment, IRubyObject baseContext)
  {
    try {
      XmlDocumentFragment xmlDocumentFragment = (XmlDocumentFragment) fragment;
      org.w3c.dom.DocumentFragment fragmentNode = (DocumentFragment) xmlDocumentFragment.getNode();
      XmlNode xmlNode = null;
      if (baseContext instanceof XmlNode) {
        xmlNode = (XmlNode) baseContext;
      } else if (baseContext instanceof RubyString) {
        String tagName = baseContext.asJavaString();
        Element element = (Element) fragmentNode.getOwnerDocument().createElement(tagName);
        xmlNode = new XmlNode(context.runtime, klass, element);
      } else {
        throw new IllegalArgumentException("Invalid context type: " + baseContext.getClass());
      }
      org.w3c.dom.Node baseContextNode = xmlNode.getNode();
      String url = baseContextNode.getBaseURI() == null ? "" : baseContextNode.getBaseURI();
      List<Node> children = do_parse_fragment((Element)baseContextNode, url);
      ((Document)fragmentNode.getOwnerDocument()).outputSettings().charset(StandardCharsets.UTF_8);
      for (Node child : children) {
        org.w3c.dom.Node adopted = fragmentNode.getOwnerDocument().adoptNode(child);
        fragmentNode.appendChild(adopted);
      }
      //addErrorsIfNecessary(context, xmlDoc); // TODO: needs the way to pass errors
      return xmlDocumentFragment;
    } catch (Exception e) {
      // TODO: consider a much better exception handling
      XmlSyntaxError xmlSyntaxError = XmlSyntaxError.createXMLSyntaxError(context.runtime);
      xmlSyntaxError.setException(e);
      throw xmlSyntaxError.toThrowable();
    }
  }

  protected List<Node>
  do_parse_fragment(Element element, String url) throws SAXException, IOException
  {
    Reader reader = new InputStreamReader(getInputSource().getByteStream(), java_encoding);
    return parser.parseFragmentInput(reader, element, url);
  }
}
