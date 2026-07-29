package nokogiri.internals;

import nokogiri.Html5Document;
import nokogiri.XmlDocument;
import nokogiri.XmlSyntaxError;
import nokogiri.internals.html5.parser.Parser;
import org.jruby.*;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
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
  protected transient NokogiriErrorHandler errorHandler;
  protected transient Parser parser;
  protected transient IRubyObject ruby_encoding;

  public Html5ParserContext(Ruby runtime, IRubyObject options)
  {
    this(runtime, runtime.getNil(), options);
  }

  public Html5ParserContext(Ruby runtime, IRubyObject encoding, IRubyObject options)
  {
    super(runtime);
    this.options = new ParserContext.Options(RubyFixnum.fix2long(options));
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
    parser.setTrackErrors(100);
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
    doc.setInstanceVariable("@errors", mapErrors(context, errorHandler));
  }

  public static RubyArray<?>
  mapErrors(ThreadContext context, NokogiriErrorHandler errorHandler)
  {
    final Ruby runtime = context.runtime;
    final List<RubyException> errors = errorHandler.getErrors();
    final IRubyObject[] errorsAry = new IRubyObject[errors.size()];
    for (int i = 0; i < errors.size(); i++) {
      errorsAry[i] = errors.get(i);
    }
    return runtime.newArrayNoCopy(errorsAry);
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
      xmlDoc = wrapDocument(context, klass, doc);
      xmlDoc.setUrl(url);
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
    // TODO: figure out how to get base url.
    Document document = parser.parseInput(getInputSource().getCharacterStream(), "");
//    if (options.noBlanks) {
//      List<Node> emptyNodes = new ArrayList<Node>();
//      findEmptyTexts(parser.getDocument(), emptyNodes);
//      if (emptyNodes.size() > 0) {
//        for (Node node : emptyNodes) {
//          node.getParentNode().removeChild(node);
//        }
//      }
//    }
    return document;
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
    htmlDocument.setEncoding(ruby_encoding);
    htmlDocument.setParsedEncoding(java_encoding);
    return htmlDocument;
  }
}
