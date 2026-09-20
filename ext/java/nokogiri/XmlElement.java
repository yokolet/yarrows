package nokogiri;

import nokogiri.internals.NokogiriHelpers;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import nokogiri.internals.SaveContextVisitor;

/**
 * Class for Nokogiri::XML::Element
 *
 * @author sergio
 * @author Yoko Harada <yokolet@gamil.com>
 */
@JRubyClass(name = "Nokogiri::XML::Element", parent = "Nokogiri::XML::Node")
public class XmlElement extends XmlNode
{
  private static final long serialVersionUID = 1L;

  public
  XmlElement(Ruby runtime, RubyClass klazz)
  {
    super(runtime, klazz);
  }

  // unused
  @Deprecated
  public
  XmlElement(Ruby runtime, RubyClass klazz, Node element)
  {
    super(runtime, klazz, element);
  }

  @Override
  public void
  accept(ThreadContext context, SaveContextVisitor visitor)
  {
    visitor.enter((Element) node);
    acceptChildren(context, getChildren(), visitor);
    visitor.leave((Element) node);
  }

  // This method is added to handle write_to method of HTML5::Node
  @JRubyMethod(name = "html_standard_serialize")
  public IRubyObject html_standard_serialize(ThreadContext context, IRubyObject option)
  {
    Boolean preserve_newline = option.toJava(Boolean.class);
    String output = null;
    if (getNode() instanceof nokogiri.internals.html5.nodes.Node) {
      nokogiri.internals.html5.nodes.Node innerNode = (nokogiri.internals.html5.nodes.Node) getNode();
      nokogiri.internals.html5.nodes.Document innerDoc = (nokogiri.internals.html5.nodes.Document) innerNode.getOwnerDocument();
      innerDoc.outputSettings().prettyPrint(preserve_newline);
      output = innerNode.outerHtml();
    } else {
      getNode().toString();
    }
    return NokogiriHelpers.stringOrBlank(context.runtime, output);
  }
}
