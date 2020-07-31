package com.redbeemedia.enigma.exoplayerdownload;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class WidevineHelperTest {
    @Test
    public void testFindContentProtectionTags() throws ParserConfigurationException, IOException, SAXException {
        final String CONTENT_PROTECTION = "ContentProtection";
        final UUID widevineUUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
        String mockDocument;
        {
            ITag mpd = new Tag("MPD");
            ITag period = mpd.buildChild("Period");
            period.buildChild(CONTENT_PROTECTION);
            ITag adaptionSet = period.buildChild("AdaptionSet");
            adaptionSet.buildChild("Other");
            adaptionSet.buildChild(CONTENT_PROTECTION)
                .attr("test", "JUnit")
                .attr("schemeIdUri", "urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED");
            adaptionSet.buildChild(CONTENT_PROTECTION)
                .attr("schemeIdUri", "urn:uuid:AAEF8BA9-1234-1234-1234-27DCD51D21ED");
            adaptionSet = period.buildChild("AdaptionSet");
            adaptionSet.buildChild(CONTENT_PROTECTION)
                .attr("schemeIdUri", "urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED")
                .attr("extraData", "Extra!");
            adaptionSet.buildChild(CONTENT_PROTECTION)
                .attr("schemeIdUri", "urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED")
                .attr("extraData", "Even more extra!");
            mpd.buildChild(CONTENT_PROTECTION)
                .attr("schemeIdUri", "urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED")
                .attr("id", "737");
            mpd.buildChild(CONTENT_PROTECTION)
                    .attr("schemeIdUri", "urn:uuid:AAEF8BA9-1234-1234-1234-27DCD51D21ED")
                    .attr("id", "999");
            mpd.buildChild(CONTENT_PROTECTION)
                    .attr("schemeIdUri", "urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED")
                    .attr("id", "-7");
            mockDocument = mpd.toString();
        }
        Document document = toDocument(mockDocument);

        List<Element> contentProtectionElements = WidevineHelper.findContentProtectionTags(document.getDocumentElement(), widevineUUID);

        Assert.assertEquals(5, contentProtectionElements.size());

        Element theOneWithTest = null;
        Element theOneWithExtraData = null;
        Element theOtherOneWithExtraData = null;
        Element theOneWithPositiveId = null;
        Element theOneWithNegativeId = null;
        for(Element element : contentProtectionElements) {
            if(element.hasAttribute("test") && "JUnit".equals(element.getAttribute("test"))) {
                theOneWithTest = element;
                continue;
            } else if(element.hasAttribute("extraData")) {
                String extraData = element.getAttribute("extraData");
                if("Extra!".equals(extraData)) {
                    theOneWithExtraData = element;
                    continue;
                } else if("Even more extra!".equals(extraData)) {
                    theOtherOneWithExtraData = element;
                    continue;
                } else {
                    Assert.fail(extraData);
                }
            } else if(element.hasAttribute("id")) {
                String id = element.getAttribute("id");
                if("737".equals(id)) {
                    theOneWithPositiveId = element;
                    continue;
                } else if("-7".equals(id)) {
                    theOneWithNegativeId = element;
                    continue;
                } else {
                    Assert.fail(id);
                }
            }
        }

        Assert.assertNotNull("Missing element",theOneWithTest);
        Assert.assertNotNull("Missing element", theOneWithExtraData);
        Assert.assertNotNull("Missing element", theOtherOneWithExtraData);
        Assert.assertNotNull("Missing element", theOneWithPositiveId);
        Assert.assertNotNull("Missing element", theOneWithNegativeId);
    }


    @Test
    public void testGetPssh() throws IOException, SAXException, ParserConfigurationException {
        Document document = toDocument("<ContentProtection xmlns=\"urn:mpeg:dash:schema:mpd:2011\" schemeIdUri=\"urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED\">\n" +
                "        <cenc:pssh xmlns:cenc=\"urn:mpeg:cenc:2013\">base64data</cenc:pssh>\n" +
                "      </ContentProtection>");
        List<Element> elements = Arrays.asList(document.getDocumentElement());

        String pssh = WidevineHelper.getPssh(elements);
        Assert.assertNotNull(pssh);
        Assert.assertEquals("base64data", pssh);
    }

    private static Document toDocument(String string) throws ParserConfigurationException, IOException, SAXException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(string)));
    }

    private interface ITag {
        ITag attr(String name, String value);
        ITag buildChild(String name);
    }

    private static class Tag implements ITag {
        private final String name;
        private final List<Tag> children = new ArrayList<>();
        private final Map<String, String> attributes = new LinkedHashMap<>();

        public Tag(String name) {
            this.name = name;
        }

        @Override
        public Tag attr(String name, String value) {
            attributes.put(name, value);
            return this;
        }

        @Override
        public Tag buildChild(String name) {
            Tag child = new Tag(name);
            children.add(child);
            return child;
        }

        private void toString(StringBuilder stringBuilder) {
            stringBuilder.append("<").append(name);
            for(Map.Entry<String, String> attribute : attributes.entrySet()) {
                stringBuilder.append(" ").append(attribute.getKey()).append("=\"");
                stringBuilder.append(makeXmlCompliant(attribute.getValue()));
                stringBuilder.append("\"");
            }
            if(children.isEmpty()) {
                stringBuilder.append(" />");
            } else {
                stringBuilder.append(">");
                for(Tag child : children) {
                    child.toString(stringBuilder);
                }
                stringBuilder.append("</").append(name).append(">");
            }
        }

        private static String makeXmlCompliant(String value) {
            value = value.replace("&", "&amp;");
            value = value.replace("<", "&lt;");
            value = value.replace(">", "&gt;");
            value = value.replace("\"", "&quot;");
            value = value.replace("'", "&apos;");
            return value;
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            this.toString(stringBuilder);
            return stringBuilder.toString();
        }
    }
}
