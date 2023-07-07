package ugh.fileformats.mets;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;

public class RulesetExtension {

    private RulesetExtension() {
    }

    public static void extentRuleset(SubnodeConfiguration config, MetsModsImportExport fileformat) {

        // for each configured field
        List<HierarchicalConfiguration> list = config.configurationsAt("/additionalMetadata/Metadata");
        for (HierarchicalConfiguration hc : list) {
            String internalName = hc.getString("/InternalName");
            String xpath = hc.getString("/WriteXPath");

            // check, if an export mapping exists
            boolean matchFound = false;
            for (MatchingMetadataObject mmo : fileformat.getModsNamesMD()) {
                if (mmo.getInternalName().equals(internalName)) {
                    matchFound = true;
                }
            }
            // if not, add a new mapping to the list
            if (!matchFound) {
                MatchingMetadataObject mmo = new MatchingMetadataObject();
                mmo.setInternalName(internalName);
                mmo.setWriteXQuery(xpath);
                fileformat.getModsNamesMD().add(mmo);
            }
        }

        List<HierarchicalConfiguration> groups = config.configurationsAt("/additionalMetadata/Group");

        for (HierarchicalConfiguration group : groups) {
            String internalName = group.getString("/InternalName");
            String xpath = group.getString("/WriteXPath");

            // check, if an export mapping exists
            boolean matchFound = false;
            for (MatchingMetadataObject mmo : fileformat.getModsNamesMD()) {
                if (mmo.getInternalName().equals(internalName)) {
                    matchFound = true;
                    break;
                }
            }
            // if not, add a new mapping to the list
            if (!matchFound) {
                MatchingMetadataObject mmo = new MatchingMetadataObject();
                mmo.setInternalName(internalName.trim());
                mmo.setWriteXQuery(xpath.trim());

                List<HierarchicalConfiguration> metadataList = group.configurationsAt("Metadata");
                for (HierarchicalConfiguration md : metadataList) {
                    String metadataName = md.getString("InternalName");
                    String metadataXpath = md.getString("WriteXPath");
                    Map<String, String> map = new LinkedHashMap<>();
                    map.put(metadataName, metadataXpath);
                    mmo.addToMap(metadataName, map);
                }

                metadataList = group.configurationsAt("Person");
                for (HierarchicalConfiguration md : metadataList) {
                    parsePerson(mmo, md);

                }
                fileformat.getModsNamesMD().add(mmo);
            }
        }
    }

    private static void parsePerson(MatchingMetadataObject mmo, HierarchicalConfiguration md) {
        String elementName = md.getString("InternalName");
        Map<String, String> map = new HashMap<>();

        String value = md.getString("WriteXPath");
        if (value != null) {
            map.put("WriteXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }
        value = md.getString("LastnameXPath");
        if (value != null) {
            map.put("LastnameXPath", value);
        }
        value = md.getString("DisplayNameXPath");
        if (value != null) {
            map.put("DisplayNameXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }
        value = md.getString("FirstnameXPath");
        if (value != null) {
            map.put("FirstnameXPath", value);
        }

        mmo.addToMap(elementName, map);
    }
}
