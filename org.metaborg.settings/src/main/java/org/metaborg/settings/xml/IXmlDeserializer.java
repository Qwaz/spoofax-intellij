package org.metaborg.settings.xml;

import org.metaborg.settings.Settings;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * XML deserializer.
 */
public interface IXmlDeserializer {

    /**
     * Deserializes an object.
     */
    Object deserialize(
            Node node,
            XmlSettingsFormat format);

}
