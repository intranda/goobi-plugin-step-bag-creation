package ugh.fileformats.mets;

import java.util.List;

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
            for (MatchingMetadataObject mmo : fileformat.modsNamesMD) {
                if (mmo.getInternalName().equals(internalName)) {
                    matchFound = true;
                }
            }
            // if not, add a new mapping to the list
            if (!matchFound) {
                MatchingMetadataObject mmo = new MatchingMetadataObject();
                mmo.setInternalName(internalName);
                mmo.setWriteXQuery(xpath);
                fileformat.modsNamesMD.add(mmo);
            }
        }
    }
}
