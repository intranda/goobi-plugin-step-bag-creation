/**
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi-workflow
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package de.intranda.goobi.plugins;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.Step;
import org.goobi.production.GoobiVersion;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.export.download.ExportMets;
import de.sub.goobi.helper.BagCreation;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.XmlTools;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.files.TarUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.dl.VirtualFileGroup;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsModsImportExport;
import ugh.fileformats.mets.RulesetExtension;

@PluginImplementation
@Log4j2
public class BagcreationStepPlugin extends ExportMets implements IStepPluginVersion2 {

    private static final Namespace metsNamespace = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");
    private static final Namespace modsNamespace = Namespace.getNamespace("mods", "http://www.loc.gov/mods/v3");
    private static final Namespace sipNamespace = Namespace.getNamespace("sip", "https://DILCIS.eu/XML/METS/SIPExtensionMETS");
    private static final Namespace csipNamespace = Namespace.getNamespace("csip", "https://DILCIS.eu/XML/METS/CSIPExtensionMETS");
    private static final Namespace xlinkNamespace = Namespace.getNamespace("xlink", "http://www.w3.org/1999/xlink");
    private static final Namespace dvNamespace = Namespace.getNamespace("dv", "http://dfg-viewer.de/");
    private static final Namespace xsiNamespace = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");

    private static final long serialVersionUID = 211912948222450125L;
    @Getter
    private String title = "intranda_step_bagcreation";
    @Getter
    private Step step;

    private Process process;

    private Project project;

    private Prefs prefs;

    @Getter
    private String returnPath;

    @Setter
    // option keep temp files after bag creation, used for junit tests
    private boolean keepTempFiles = false;

    @Getter
    private transient BagCreation bag;

    private List<ProjectFileGroup> filegroups = new ArrayList<>();

    // rights information
    private String rightsOwner;
    private String rightsOwnerLogo;
    private String rightsOwnerSiteURL;
    private String rightsOwnerContact;
    private String metsRightsLicense;
    private String metsRightsSponsor;
    private String metsRightsSponsorLogo;
    private String metsRightsSponsorSiteURL;

    // links to presentation
    private String digiprovPresentation;
    private String digiprovPresentationAnchor;

    // links to anchor/child file
    private String digiprovReference;
    private String digiprovReferenceAnchor;
    // urls
    private String iiifUrl;
    private String sruUrl;

    // manifestation metadata
    private String organizationName;
    private String organizationAddress;
    private String organizationIdentifier;

    private String profileIdentifier;

    private String contactName;
    private String contactEmail;
    private String softwareName;

    private SubnodeConfiguration config;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        process = step.getProzess();
        project = process.getProjekt();
        prefs = process.getRegelsatz().getPreferences();

        // read parameters from correct block in configuration file
        config = ConfigPlugins.getProjectAndStepConfig(title, step);
        List<HierarchicalConfiguration> grps = config.configurationsAt("/filegroups/group");
        for (HierarchicalConfiguration hc : grps) {
            ProjectFileGroup group = new ProjectFileGroup();
            group.setName(hc.getString("@fileGrpName", ""));
            group.setPath(hc.getString("@prefix", ""));
            group.setMimetype(hc.getString("@mimeType", ""));
            group.setSuffix(hc.getString("@suffix", ""));
            group.setFolder(hc.getString("@folder", ""));
            group.setUseOriginalFiles(hc.getBoolean("@useOriginalFileExtension", false));
            filegroups.add(group);
        }

        rightsOwner = config.getString("/metsParameter/rightsOwner", "");
        rightsOwnerLogo = config.getString("/metsParameter/rightsOwnerLogo", "");
        rightsOwnerSiteURL = config.getString("/metsParameter/rightsOwnerSiteURL", "");
        rightsOwnerContact = config.getString("/metsParameter/rightsOwnerContact", "");
        metsRightsLicense = config.getString("/metsParameter/metsRightsLicense", "");
        metsRightsSponsor = config.getString("/metsParameter/metsRightsSponsor", "");
        metsRightsSponsorLogo = config.getString("/metsParameter/metsRightsSponsorLogo", "");
        metsRightsSponsorSiteURL = config.getString("/metsParameter/metsRightsSponsorSiteURL", "");

        digiprovPresentation = config.getString("/metsParameter/digiprovPresentation", "");
        digiprovPresentationAnchor = config.getString("/metsParameter/digiprovPresentationAnchor", "");
        digiprovReference = config.getString("/metsParameter/digiprovReference", "");
        digiprovReferenceAnchor = config.getString("/metsParameter/digiprovReferenceAnchor", "");
        iiifUrl = config.getString("/metsParameter/iiifUrl", "");
        sruUrl = config.getString("/metsParameter/sruUrl", "");

        organizationName = config.getString("/submissionParameter/organizationName", "");
        organizationAddress = config.getString("/submissionParameter/organizationAddress", "");
        organizationIdentifier = config.getString("/submissionParameter/organizationIdentifier", "");
        contactName = config.getString("/submissionParameter/contactName", "");
        contactEmail = config.getString("/submissionParameter/contactEmail", "");
        softwareName = config.getString("/submissionParameter/softwareName", "");
        profileIdentifier = config.getString("/submissionParameter/profileIdentifier", "");

    }

    @Override
    public PluginReturnValue run() { //NOSONAR
        String identifier = null;
        VariableReplacer vp = null;
        Map<String, FileList> files = new HashMap<>();

        try {
            // read metadata
            Fileformat fileformat = process.readMetadataFile();

            DigitalDocument dd = fileformat.getDigitalDocument();

            // find DOI metadata
            DocStruct ds = dd.getLogicalDocStruct();
            if (ds.getType().isAnchor()) {
                ds = ds.getAllChildren().get(0);
            }
            for (Metadata md : ds.getAllMetadata()) {
                if ("DOI".equals(md.getType().getName())) {
                    identifier = md.getValue();
                    break;
                }
            }
            // if DOI is missing, use CatalogIDDigital
            if (identifier == null) {
                for (Metadata md : ds.getAllMetadata()) {
                    if ("CatalogIDDigital".equals(md.getType().getName())) {
                        identifier = md.getValue();
                        break;
                    }
                }
            }

            if (identifier == null) {
                // no identifier found, cancel export
                return PluginReturnValue.ERROR;
            }

            DocStruct physical = dd.getPhysicalDocStruct();
            // missing pagination, try to create a new one
            // if pagination is missing, we might have subfolder instead of files in master folder

            createPagination(dd, ds, physical);

            bag = new BagCreation(ConfigurationHelper.getInstance().getTemporaryFolder() + "/" + identifier.replace("/", "_"));
            bag.createIEFolder(identifier.replace("/", "_"), "representations");

            vp = new VariableReplacer(fileformat.getDigitalDocument(), prefs, process, null);
            // create export file

            MetsModsImportExport exportFilefoExport = new MetsModsImportExport(prefs);

            RulesetExtension.extentRuleset(config, exportFilefoExport);

            exportFilefoExport.setDigitalDocument(fileformat.getDigitalDocument());
            exportFilefoExport.setWriteLocal(false);
            exportFilefoExport.getDigitalDocument().addAllContentFiles();

            // write process id as metadata
            Metadata processid = new Metadata(prefs.getMetadataTypeByName("_PROCESSID"));
            processid.setValue(String.valueOf(process.getId()));
            ds.addMetadata(processid);

            // generate uuids
            exportFilefoExport.setCreateUUIDs(true);

            // create filegroups for each folder/representation
            for (ProjectFileGroup projectFileGroup : filegroups) {
                // check if folder exists
                Path sourceFolder = getSourceFolder(projectFileGroup.getFolder());

                if (sourceFolder != null && StorageProvider.getInstance().isFileExists(sourceFolder)) {

                    FileList fl = new FileList();
                    fl.setFileGroupName(projectFileGroup.getName());
                    fl.setSourceFolder(sourceFolder);
                    fl.setFiles(getFolderContent(sourceFolder));
                    fl.setMimetype(projectFileGroup.getMimetype());
                    fl.setUseOrigFileExtension(projectFileGroup.isUseOriginalFiles());
                    files.put(projectFileGroup.getName(), fl);
                    // generate filegroup
                    VirtualFileGroup virt = new VirtualFileGroup(projectFileGroup.getName(), projectFileGroup.getPath(),
                            projectFileGroup.getMimetype(), projectFileGroup.getSuffix());
                    virt.setIgnoreConfiguredMimetypeAndSuffix(projectFileGroup.isUseOriginalFiles());
                    exportFilefoExport.getDigitalDocument().getFileSet().addVirtualFileGroup(virt);
                }
            }
            // add a filegroup for original data
            VirtualFileGroup virt = new VirtualFileGroup("Other", "other", "application/xml", "xml");
            virt.setIgnoreConfiguredMimetypeAndSuffix(true);
            exportFilefoExport.getDigitalDocument().getFileSet().addVirtualFileGroup(virt);

            // copy meta.xml and meta_anchor.xml

            Path otherMetadataFolder = Paths.get(bag.getOtherFolder().toString());
            Path metaFile = Paths.get(process.getMetadataFilePath());
            Path metaAnchorFile = Paths.get(process.getMetadataFilePath().replace(".xml", "_anchor.xml"));
            List<Path> metadataFiles = new ArrayList<>();
            StorageProvider.getInstance().createDirectories(otherMetadataFolder);
            if (StorageProvider.getInstance().isFileExists(metaFile)) {
                Path destination = Paths.get(otherMetadataFolder.toString(), "meta.xml");
                StorageProvider.getInstance().copyFile(metaFile, destination);
                metadataFiles.add(destination);
            }
            if (StorageProvider.getInstance().isFileExists(metaAnchorFile)) {
                Path destination = Paths.get(otherMetadataFolder.toString(), "meta_anchor.xml");
                StorageProvider.getInstance().copyFile(metaAnchorFile, destination);
                metadataFiles.add(destination);
            }

            FileList metadata = new FileList();
            metadata.setFileGroupName("Other");
            metadata.setSourceFolder(metaFile.getParent());
            metadata.setFiles(metadataFiles);
            files.put("Other", metadata);

            // project parameter
            setProjectParameter(identifier, vp, exportFilefoExport);

            // save file
            exportFilefoExport.write(bag.getIeFolder().toString() + "/METS.xml");

            // check if anchor exists
            Path anchorFile = Paths.get(bag.getIeFolder().toString(), "/METS_anchor.xml");
            if (StorageProvider.getInstance().isFileExists(anchorFile)) {
                Path destination = Paths.get(otherMetadataFolder.toString(), "METS_anchor.xml");
                changeAnchorFile(destination, anchorFile);
                metadataFiles.add(destination);
            }

        } catch (UGHException | IOException | SwapException e) {
            log.error(e);
        }
        // open exported file to enhance it
        try {
            Document doc = XmlTools.getSAXBuilder().build(bag.getIeFolder().toString() + "/METS.xml");
            Element mets = doc.getRootElement();

            mets.addNamespaceDeclaration(sipNamespace);
            mets.addNamespaceDeclaration(csipNamespace);
            mets.setAttribute("TYPE", "Mixed"); // CSIP2
            mets.setAttribute("PROFILE", "https://earksip.dilcis.eu/profile/E-ARK-SIP.xml"); // SIP2
            mets.setAttribute("CONTENTINFORMATIONTYPE", "MIXED", csipNamespace); // CSIP4

            // enhance existing agent, add additional user agent for submitting agent (SIP4 - SIP 31)
            String creationDate = createUserAgent(mets);

            // enhance dmdSecs
            String dmdIds = changeDmdSecs(mets, creationDate);

            changeAmdSec(mets, creationDate, "");

            changeFileSec(files, mets, creationDate);

            changeStructMap(mets, identifier, dmdIds);

            removeStructLinks(mets);

            cleanUpNamespacesAndSchemaLocation(mets);
            // save enhanced file
            XMLOutputter xmlOut = new XMLOutputter(Format.getPrettyFormat());
            FileOutputStream fos = new FileOutputStream(bag.getIeFolder() + "/METS.xml");
            xmlOut.output(doc, fos);
            fos.close();

            createBag(identifier);

        } catch (JDOMException | IOException e) {
            log.error(e);
        }

        try {
            TarUtils.createTar(bag.getBagitRoot(), Paths.get(process.getProcessDataDirectory(), identifier.replace("/", "_") + ".tar"));
        } catch (IOException | SwapException e) {
            log.error(e);
        }

        // clean up temporary files after file was created
        if (!keepTempFiles) {
            Path folder = bag.getBagitRoot();
            StorageProvider.getInstance().deleteDir(folder);
        }
        return PluginReturnValue.FINISH;
    }

    private void changeAnchorFile(Path destinationAnchorFile, Path anchorFile) {
        try {
            // open file
            Document anchorDoc = XmlTools.getSAXBuilder().build(anchorFile.toString());
            Element mets = anchorDoc.getRootElement();
            Element logicalDiv = mets.getChild("structMap", metsNamespace).getChild("div", metsNamespace);
            Element mptr = logicalDiv.getChild("div", metsNamespace).getChild("mptr", metsNamespace);

            String creationDate = createUserAgent(mets);

            // amdSec
            changeAmdSec(mets, creationDate, "-anchor");

            // change dmdSec
            Element dmdSec = mets.getChild("dmdSec", metsNamespace);
            String dmdSecId = "MODS-" + dmdSec.getAttributeValue("ID");
            dmdSec.setAttribute("ID", dmdSecId);
            logicalDiv.setAttribute("DMDID", dmdSecId);
            dmdSec.setAttribute("CREATED", creationDate); // CSIP19
            dmdSec.setAttribute("STATUS", "CURRENT"); // CSIP20

            // get mods element
            Element mdWrap = dmdSec.getChild("mdWrap", metsNamespace);
            Element xmlData = mdWrap.getChild("xmlData", metsNamespace);
            Element mods = xmlData.getChild("mods", modsNamespace);
            // create deep copy
            Element copy = mods.clone();

            // create external file, remove content from dmdSec, add file reference to dmdSec,
            Element mdRef = createMetadataFile(copy, "metadata/", "descriptive/", dmdSecId,
                    "http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd");
            dmdSec.removeContent(mdWrap);
            dmdSec.addContent(mdRef);

            // update link in structMap
            mptr.setAttribute("href", "../../../METS.xml", xlinkNamespace);

            // save new file
            XMLOutputter xmlOut = new XMLOutputter(Format.getPrettyFormat());
            FileOutputStream fos = new FileOutputStream(anchorFile.toFile());
            xmlOut.output(anchorDoc, fos);
            fos.close();

            // move anchor file to other/METS_anchor.xml
            StorageProvider.getInstance().move(anchorFile, destinationAnchorFile);

        } catch (IOException | JDOMException e) {
            log.error(e);
        }
    }

    private void createPagination(DigitalDocument dd, DocStruct ds, DocStruct physical) {
        try {
            if (physical.getAllChildren() == null || dd.getFileSet() == null || dd.getFileSet().getAllFiles().isEmpty()) {
                Path masterfolder = Paths.get(process.getImagesOrigDirectory(false));
                List<Path> list = getFolderContent(masterfolder);
                if (!list.isEmpty()) {
                    DocStructType docStructPage = prefs.getDocStrctTypeByName("page");
                    MetadataType physmdt = prefs.getMetadataTypeByName("physPageNumber");
                    MetadataType logmdt = prefs.getMetadataTypeByName("logicalPageNumber");
                    int currentPhysicalOrder = 0;

                    for (Path file : list) {

                        String mimetype = NIOFileUtils.getMimeTypeFromFile(file);
                        DocStruct dsPage = dd.createDocStruct(docStructPage);

                        // physical page no
                        physical.addChild(dsPage);
                        Metadata mdTemp = new Metadata(physmdt);
                        mdTemp.setValue(String.valueOf(++currentPhysicalOrder));
                        dsPage.addMetadata(mdTemp);

                        // logical page no
                        mdTemp = new Metadata(logmdt);
                        mdTemp.setValue("uncounted");

                        dsPage.addMetadata(mdTemp);
                        ds.addReferenceTo(dsPage, "logical_physical");

                        // image name
                        ContentFile cf = new ContentFile();
                        cf.setMimetype(mimetype);
                        cf.setLocation("file://" + file.toString());
                        dsPage.addContentFile(cf);
                    }
                }
            }
        } catch (DAOException | IOException | SwapException | UGHException e) {
            log.error(e);
        }
    }

    private void createBag(String identifier) {
        bag.addMetadata("Source-Organization", organizationName);
        bag.addMetadata("Organization-Address", organizationAddress);
        bag.addMetadata("Contact-Name", contactName);
        bag.addMetadata("Contact-Email", contactEmail);
        bag.addMetadata("Bagging-Software", softwareName);
        bag.addMetadata("Process-ID", String.valueOf(process.getId()));
        bag.addMetadata("External-Identifier", identifier);
        bag.addMetadata("BagIt-Profile-Identifier", profileIdentifier);
        try {
            bag.addMetadata("Bag-Size", "" + StorageProvider.getInstance().getDirectorySize(bag.getIeFolder()));
        } catch (IOException e) {
            log.error(e);
        }
        bag.createBag();
    }

    private void removeStructLinks(Element mets) {
        mets.removeChild("structLink", metsNamespace);
    }

    private void changeStructMap(Element mets, String identifier, String dmdIds) {
        List<Element> structMaps = mets.getChildren("structMap", metsNamespace);
        for (Element structMap : structMaps) {
            if ("PHYSICAL".equals(structMap.getAttributeValue("TYPE"))) {
                structMap.setAttribute("LABEL", "CSIP"); // CSIP82
                structMap.setAttribute("ID", "uuid-" + UUID.randomUUID().toString()); // CSIP83
                Element physSequence = structMap.getChild("div", metsNamespace); // CSIP84
                physSequence.setAttribute("LABEL", identifier);
                physSequence.removeAttribute("TYPE");

                physSequence.removeContent();

                Element metadataDiv = new Element("div", metsNamespace); // CSIP88
                metadataDiv.setAttribute("LABEL", "Metadata"); // CSIP88
                metadataDiv.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
                metadataDiv.setAttribute("DMDID", dmdIds);
                metadataDiv.setAttribute("ADMID", "RIGHTS DIGIPROV");

                physSequence.addContent(metadataDiv);

                // create a div lement for each fileGroup
                Element fileSec = mets.getChild("fileSec", metsNamespace);
                for (Element fileGrp : fileSec.getChildren()) {
                    String fileGroupId = fileGrp.getAttributeValue("ID");
                    String fileGroupLabel = fileGrp.getAttributeValue("USE");
                    String href = fileGroupLabel.toLowerCase() + "/METS.xml";

                    Element div = new Element("div", metsNamespace);
                    div.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
                    div.setAttribute("LABEL", fileGroupLabel);
                    physSequence.addContent(div);
                    if(fileGroupLabel.startsWith("Representations")) {
                        Element mptr = new Element("mptr", metsNamespace);
                        mptr.setAttribute("type", "simple", xlinkNamespace);
                        mptr.setAttribute("href", href, xlinkNamespace);
                        mptr.setAttribute("title", fileGroupId, xlinkNamespace);
                        mptr.setAttribute("LOCTYPE", "URL");
                        div.addContent(mptr);

                    }else {
                        Element fptr = new Element("fptr", metsNamespace);
                        fptr.setAttribute("FILEID", fileGroupId);
                        div.addContent(fptr);

                    }
                }
            }
        }
    }

    private void changeAmdSec(Element mets, String creationDate, String suffix) {

        Element amdSec = mets.getChild("amdSec", metsNamespace); // CSIP31
        if (amdSec != null) {

            Element digiprovMD = amdSec.getChild("digiprovMD", metsNamespace);
            if (digiprovMD != null) {
                digiprovMD.setAttribute("STATUS", "CURRENT"); // CSIP34
                digiprovMD.setAttribute("CREATED", creationDate);
                // generate separate files for content, create link to the file with mdRef // CSIP35 - CSIP44
                Element mdWrap = digiprovMD.getChild("mdWrap", metsNamespace);
                Element xmlData = mdWrap.getChild("xmlData", metsNamespace);
                Element links = xmlData.getChild("links", dvNamespace);
                // create deep copy
                Element copy = links.clone();
                try {
                    Element mdRef = createMetadataFile(copy, "metadata/", "other/", "DIGIPROV" + suffix, "");
                    mdRef.setAttribute("MDTYPE", "OTHER");
                    mdRef.setAttribute("OTHERMDTYPE", "DVLINKS");

                    digiprovMD.removeContent(mdWrap);
                    digiprovMD.addContent(mdRef);
                } catch (IOException e) {
                    log.error(e);
                }
            }

            Element rightsMD = amdSec.getChild("rightsMD", metsNamespace);
            if (rightsMD != null) {
                rightsMD.setAttribute("STATUS", "CURRENT"); // CSIP47
                rightsMD.setAttribute("CREATED", creationDate);

                // generate separate files for content, create link to the file with mdRef // CSIP48 - CSIP57
                Element mdWrap = rightsMD.getChild("mdWrap", metsNamespace);
                Element xmlData = mdWrap.getChild("xmlData", metsNamespace);
                Element rights = xmlData.getChild("rights", dvNamespace);
                // create deep copy
                Element copy = rights.clone();
                try {
                    Element mdRef = createMetadataFile(copy, "metadata/", "other/", "DVRIGHTS" + suffix, "");
                    mdRef.setAttribute("MDTYPE", "OTHER");
                    mdRef.setAttribute("OTHERMDTYPE", "DVRIGHTS");
                    rightsMD.removeContent(mdWrap);
                    rightsMD.addContent(mdRef);
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
    }

    private String changeDmdSecs(Element mets, String creationDate) {
        StringBuilder ids = new StringBuilder();

        List<Element> dmdSecs = mets.getChildren("dmdSec", metsNamespace);
        List<Element> logicalElements = new ArrayList<>();
        List<Element> structMaps = mets.getChildren("structMap", metsNamespace);
        for (Element structMap : structMaps) {
            if ("LOGICAL".equals(structMap.getAttributeValue("TYPE"))) {
                getAllDivElements(logicalElements, structMap);
            }
        }
        Element mainElement = dmdSecs.get(0);
        Element topElement = logicalElements.get(0);
        // check if we have an anchor element, first sub element is mets:mptr
        if (!topElement.getChildren().isEmpty()) {
            Element subElement = topElement.getChildren().get(0);
            if ("mptr".equals(subElement.getName())) {
                // update anchor link
                subElement.setAttribute("href", "other/METS_anchor.xml", xlinkNamespace);
                topElement = topElement.getChildren().get(1);
            }
        }

        mainElement.setAttribute("ID", "MODS");
        topElement.setAttribute("ADMID", "RIGHTS DIGIPROV");
        topElement.setAttribute("DMDID", "MODS");

        for (Element dmdSec : dmdSecs) {
            String dmdSecId = dmdSec.getAttributeValue("ID");
            Element logicalDiv = null;
            for (Element div : logicalElements) {
                String divId = div.getAttributeValue("DMDID");
                if (StringUtils.isNotBlank(divId) && divId.equals(dmdSecId)) {
                    logicalDiv = div;
                    break;
                }
            }

            if (dmdSecId.startsWith("DMDLOG")) {
                dmdSecId = "MODS-" + dmdSecId;
                dmdSec.setAttribute("ID", dmdSecId);
            }

            if (logicalDiv != null) {
                logicalDiv.setAttribute("DMDID", dmdSecId);
            }

            if (ids.length() > 0) {
                ids.append(" ");
            }
            ids.append(dmdSecId);

            dmdSec.setAttribute("CREATED", creationDate); // CSIP19
            dmdSec.setAttribute("STATUS", "CURRENT"); // CSIP20

            // get mods element
            Element mdWrap = dmdSec.getChild("mdWrap", metsNamespace);
            Element xmlData = mdWrap.getChild("xmlData", metsNamespace);
            Element mods = xmlData.getChild("mods", modsNamespace);

            // create deep copy
            Element copy = mods.clone();
            try {
                // create external file, remove content from dmdSec, add file reference to dmdSec,
                Element mdRef = createMetadataFile(copy, "metadata/", "descriptive/", dmdSecId,
                        "http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd");
                dmdSec.removeContent(mdWrap);
                dmdSec.addContent(mdRef);
            } catch (IOException e) {
                log.error(e);
            }

        }
        return ids.toString();
    }

    private void getAllDivElements(List<Element> logicalElements, Element structMap) {
        List<Element> divs = structMap.getChildren();
        if (!divs.isEmpty()) {
            logicalElements.addAll(divs);
            for (Element div : divs) {
                if (!div.getChildren().isEmpty()) {
                    getAllDivElements(logicalElements, div);
                }
            }
        }
    }

    private Element createMetadataFile(Element root, String metadataFolder, String subFolder, String filename, String schemaLocation)
            throws IOException {
        if (StringUtils.isNotBlank(schemaLocation)) {
            root.addNamespaceDeclaration(xsiNamespace);
            root.setAttribute("schemaLocation", schemaLocation, xsiNamespace);
        }

        Document doc = new Document();
        doc.setRootElement(root);
        Path fileName = null;
        if (StringUtils.isNotBlank(subFolder)) {
            fileName = Paths.get(bag.getMetadataFolder().toString(), subFolder, filename + ".xml");
            StorageProvider.getInstance().createDirectories(fileName.getParent());
        } else {
            fileName = Paths.get(bag.getMetadataFolder().toString(), filename + ".xml");
        }

        cleanUpNamespacesAndSchemaLocation(root);
        XMLOutputter xmlOut = new XMLOutputter(Format.getPrettyFormat());
        FileOutputStream fos = new FileOutputStream(fileName.toString());
        xmlOut.output(doc, fos);
        fos.close();

        Element mdRef = new Element("mdRef", metsNamespace);
        mdRef.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
        mdRef.setAttribute("LOCTYPE", "URL");
        mdRef.setAttribute("MDTYPE", "MODS");
        mdRef.setAttribute("MIMETYPE", "text/xml");
        mdRef.setAttribute("CHECKSUMTYPE", "SHA-256");
        mdRef.setAttribute("type", "simple", xlinkNamespace);
        mdRef.setAttribute("href", metadataFolder + subFolder + filename + ".xml", xlinkNamespace);

        mdRef.setAttribute("SIZE", "" + StorageProvider.getInstance().getFileSize(fileName));
        mdRef.setAttribute("CREATED", StorageProvider.getInstance().getFileCreationTime(fileName));
        mdRef.setAttribute("CHECKSUM", StorageProvider.getInstance().createSha256Checksum(fileName));

        return mdRef;

    }

    private String createUserAgent(Element mets) {
        /*
        <mets:agent ROLE="CREATOR" TYPE="INDIVIDUAL">
          <mets:name />
          <mets:note csip:NOTETYPE="IDENTIFICATIONCODE">1</mets:note>
        </mets:agent>
         */

        // software
        Element metsHdr = mets.getChild("metsHdr", metsNamespace);
        metsHdr.setAttribute("OAISPACKAGETYPE", "SIP", csipNamespace); // SIP4
        metsHdr.setAttribute("RECORDSTATUS", "NEW"); // SIP3
        String creationDate = metsHdr.getAttributeValue("CREATEDATE");
        Element agent = metsHdr.getChild("agent", metsNamespace);

        Element name = agent.getChild("name", metsNamespace);
        name.setText(softwareName);
        Element noteVersion = agent.getChild("note", metsNamespace);
        noteVersion.setAttribute("NOTETYPE", "SOFTWARE VERSION", csipNamespace); // SIP20
        noteVersion.setText(GoobiVersion.getBuildversion());

        // organization
        Element agent2 = new Element("agent", metsNamespace);
        agent2.setAttribute("ROLE", "CREATOR");
        agent2.setAttribute("TYPE", "ORGANIZATION");
        metsHdr.addContent(agent2);
        Element organisationName = new Element("name", metsNamespace);
        organisationName.setText(organizationName); // SIP24
        agent2.addContent(organisationName);

        Element organisationNote = new Element("note", metsNamespace);
        organisationNote.setText(organizationAddress);
        agent2.addContent(organisationNote);

        Element identificationNote = new Element("note", metsNamespace);
        identificationNote.setText(organizationIdentifier);
        identificationNote.setAttribute("NOTETYPE", "IDENTIFICATIONCODE", csipNamespace); // SIP20
        agent2.addContent(identificationNote);

        // individual
        Element agent3 = new Element("agent", metsNamespace);
        agent3.setAttribute("ROLE", "CREATOR"); // SIP16
        agent3.setAttribute("TYPE", "INDIVIDUAL"); // SIP17
        metsHdr.addContent(agent3);
        Element name3 = new Element("name", metsNamespace);
        name3.setText(contactName); // SIP24
        agent3.addContent(name3);
        Element note3 = new Element("note", metsNamespace);
        note3.setText(contactEmail);
        agent3.addContent(note3);
        return creationDate;
    }

    private void changeFileSec(Map<String, FileList> files, Element mets, String creationDate) throws IOException {
        Element fileSec = mets.getChild("fileSec", metsNamespace);
        fileSec.setAttribute("ID", "uuid-" + UUID.randomUUID().toString()); // CSIP59

        List<Element> filegroupsToDelete = new ArrayList<>();

        for (Element fileGrp : fileSec.getChildren("fileGrp", metsNamespace)) {
            fileGrp.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());

            String name = fileGrp.getAttributeValue("USE");
            FileList fl = files.get(name);
            List<Path> filesInFolder = fl.getFiles();
            String sourceFolderName = fl.getSourceFolder().toString();
            // remove filegrp if file list is empty
            if (filesInFolder.isEmpty()) {
                filegroupsToDelete.add(fileGrp);
                continue;
            }
            if (name.startsWith("Representations")) {
                List<Element> filesToDelete = new ArrayList<>();
                List<Element> filesInXml = fileGrp.getChildren("file", metsNamespace);
                for (int i = 0; i < filesInXml.size(); i++) {
                    Element fileElement = filesInXml.get(i);
                    if (filesInFolder.size() > i) {
                        Path file = filesInFolder.get(i);
                        String filename = file.toString().replace(sourceFolderName, "");
                        // checksum, filesize, changedate
                        fileElement.setAttribute("SIZE", "" + StorageProvider.getInstance().getFileSize(file)); // CSIP69
                        fileElement.setAttribute("CREATED", StorageProvider.getInstance().getFileCreationTime(file)); // CSIP70
                        fileElement.setAttribute("CHECKSUM", StorageProvider.getInstance().createSha256Checksum(file)); // CSIP71
                        fileElement.setAttribute("CHECKSUMTYPE", "SHA-256"); // CSIP72
                        Element flocat = fileElement.getChild("FLocat", metsNamespace);
                        flocat.setAttribute("type", "simple", xlinkNamespace); // CSIP78
                        flocat.setAttribute("href", "data" + filename, xlinkNamespace); // CSIP78
                    } else {
                        // if actual filesize is smaller than filegroup size, remove superfluous files
                        filesToDelete.add(fileElement);
                    }
                }
                for (Element file : filesToDelete) {
                    fileGrp.removeContent(file);
                }

                // create separate file for each fileGrp, create link to the file with mdRef (CSIP76 - SIP35)
                Element clone = fileGrp.clone();

                Element file = createFileGroupFile(mets, clone, creationDate);

                fileGrp.removeContent();
                fileGrp.addContent(file);

            } else {

                // clear existing files in filegrp
                fileGrp.removeContent();

                // for each file add a new file

                for (Path file : filesInFolder) {

                    Element fileElement = new Element("file", metsNamespace);
                    // checksum, filesize, changedate
                    fileElement.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
                    fileElement.setAttribute("SIZE", "" + StorageProvider.getInstance().getFileSize(file)); // CSIP69
                    fileElement.setAttribute("CREATED", StorageProvider.getInstance().getFileCreationTime(file)); // CSIP70
                    fileElement.setAttribute("CHECKSUM", StorageProvider.getInstance().createSha256Checksum(file)); // CSIP71
                    fileElement.setAttribute("CHECKSUMTYPE", "SHA-256"); // CSIP72

                    Element flocat = new Element("FLocat", metsNamespace);

                    String filename = file.toString().replace(sourceFolderName, "");

                    flocat.setAttribute("LOCTYPE", "URL");
                    flocat.setAttribute("type", "simple", xlinkNamespace); // CSIP78
                    if ("Other".equals(name)) {
                        fileElement.setAttribute("MIMETYPE", "text/xml");
                        flocat.setAttribute("href", "other/" + file.getFileName().toString(), xlinkNamespace); // CSIP78
                    } else {
                        String mimetype;
                        if (fl.isUseOrigFileExtension()) {
                            mimetype = NIOFileUtils.getMimeTypeFromFile(file);
                        } else {
                            mimetype = fl.getMimetype();
                        }
                        fileElement.setAttribute("MIMETYPE", mimetype);
                        flocat.setAttribute("href", fl.getFileGroupName().toLowerCase() + filename, xlinkNamespace); // CSIP78
                    }
                    fileElement.addContent(flocat);
                    fileGrp.addContent(fileElement);
                }
            }
        }

        for (Element fileGroup : filegroupsToDelete) {
            fileSec.removeContent(fileGroup);
        }

        // copy files
        for (Entry<String, FileList> entry : files.entrySet()) {

            String folderName = entry.getKey().replace("Representations/", "").replace("Documentation/", "");
            Path sourceFolder = entry.getValue().getSourceFolder();

            Path destinationFolder = null;
            if (entry.getKey().startsWith("Representations")) {
                destinationFolder = Paths.get(bag.getObjectsFolder().toString(), folderName, "data");
            } else if (entry.getKey().startsWith("Other")) {
                continue;
            } else {
                destinationFolder = Paths.get(bag.getDocumentationFolder().toString());
            }

            StorageProvider.getInstance().createDirectories(destinationFolder);
            StorageProvider.getInstance().copyDirectory(sourceFolder, destinationFolder);
        }

    }

    private Element createFileGroupFile(Element oldMets, Element fileGrp, String creationDate) throws IOException {
        String use = fileGrp.getAttributeValue("USE");
        fileGrp.setAttribute("USE", "Data"); // replace use value
        String fileGrpType = use.replace("Representations/", "").replace("Documentation/", "").replace("Other/", "");

        int numberOfFiles = 0;
        List<String> fileIdentifier = new ArrayList<>();
        for (Element file : fileGrp.getChildren("file", metsNamespace)) {
            numberOfFiles++;
            String fileId = file.getAttributeValue("ID");
            fileIdentifier.add(fileId);
            if (!fileId.startsWith("uuid")) {
                file.setAttribute("ID", "uuid-" + fileId);
            }
        }

        //  create new mets file
        Element metsRoot = new Element("mets", metsNamespace);
        metsRoot.addNamespaceDeclaration(sipNamespace);
        metsRoot.addNamespaceDeclaration(csipNamespace);
        metsRoot.addNamespaceDeclaration(xlinkNamespace);
        metsRoot.addNamespaceDeclaration(xsiNamespace);
        metsRoot.setAttribute("OBJID", fileGrpType);
        metsRoot.setAttribute("LABEL", fileGrpType + " copy");
        metsRoot.setAttribute("TYPE", "Mixed");
        metsRoot.setAttribute("CONTENTINFORMATIONTYPE", "MIXED", csipNamespace);
        metsRoot.setAttribute("PROFILE", "https://earksip.dilcis.eu/profile/E-ARK-SIP.xml");
        metsRoot.setAttribute("schemaLocation",
                "http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd http://www.loc.gov/METS/ https://www.loc.gov/standards/mets/version112/mets.xsd",
                xsiNamespace);
        Element metsHdr = new Element("metsHdr", metsNamespace);
        metsHdr.setAttribute("CREATEDATE", creationDate);
        metsHdr.setAttribute("LASTMODDATE", creationDate);
        metsHdr.setAttribute("RECORDSTATUS", "NEW");
        metsHdr.setAttribute("OAISPACKAGETYPE", "SIP", csipNamespace);
        metsRoot.addContent(metsHdr);
        Element fileSec = new Element("fileSec", metsNamespace);
        fileSec.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
        metsRoot.addContent(fileSec);
        fileSec.addContent(fileGrp);

        // structMap
        List<String> pageIDs = new ArrayList<>();
        List<Element> structMaps = oldMets.getChildren("structMap", metsNamespace);
        for (Element structMap : structMaps) {
            if ("PHYSICAL".equals(structMap.getAttributeValue("TYPE"))) {
                pageIDs = createPhysicalStructMap(fileGrpType, fileIdentifier, metsRoot, structMap, numberOfFiles);
            }

        }

        // structLink
        Element structLink = oldMets.getChild("structLink", metsNamespace).clone();
        //  remove non existing, superfluous files
        List<Element> smLinkRemoveList = new ArrayList<>();

        for (Element smLink : structLink.getChildren()) {
            String toId = smLink.getAttributeValue("to", xlinkNamespace);
            if (pageIDs.contains(toId)) {
                smLink.setAttribute("from", "../../METS.xml#" + smLink.getAttributeValue("from", xlinkNamespace), xlinkNamespace);
            } else {
                smLinkRemoveList.add(smLink);
            }
        }

        for (Element page : smLinkRemoveList) {
            structLink.removeContent(page);
        }

        metsRoot.addContent(structLink);

        Document doc = new Document();
        doc.setRootElement(metsRoot);
        Path fileName = null;

        if (use.startsWith("Representations")) {
            fileName = Paths.get(bag.getObjectsFolder().toString(), fileGrpType, "METS.xml");
        } else if (use.startsWith("Documentation")) {
            fileName = Paths.get(bag.getDocumentationFolder().toString(), fileGrpType, "METS.xml");
        } else {
            //other
            fileName = Paths.get(bag.getOtherFolder().toString(), fileGrpType, "METS.xml");
        }

        StorageProvider.getInstance().createDirectories(fileName.getParent());

        cleanUpNamespacesAndSchemaLocation(metsRoot);
        XMLOutputter xmlOut = new XMLOutputter(Format.getPrettyFormat());
        FileOutputStream fos = new FileOutputStream(fileName.toString());
        xmlOut.output(doc, fos);
        fos.close();

        Element file = new Element("file", metsNamespace);

        file.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
        file.setAttribute("MIMETYPE", "text/xml");
        file.setAttribute("SIZE", "" + StorageProvider.getInstance().getFileSize(fileName));
        file.setAttribute("CREATED", StorageProvider.getInstance().getFileCreationTime(fileName));
        file.setAttribute("CHECKSUM", StorageProvider.getInstance().createSha256Checksum(fileName));
        file.setAttribute("CHECKSUMTYPE", "SHA-256");

        Element flocat = new Element("FLocat", metsNamespace);
        flocat.setAttribute("type", "simple", xlinkNamespace);
        flocat.setAttribute("href", use.toLowerCase() + "/METS.xml", xlinkNamespace);
        flocat.setAttribute("LOCTYPE", "URL");
        file.addContent(flocat);
        return file;
    }

    private List<String> createPhysicalStructMap(String fileGrpType, List<String> fileIdentifier, Element metsRoot, Element structMap,
            int numberOfFiles) {
        Element physSequence = structMap.getChild("div", metsNamespace).clone();

        Element physicalStructMap = new Element("structMap", metsNamespace);
        physicalStructMap.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
        physicalStructMap.setAttribute("TYPE", "PHYSICAL");
        physicalStructMap.setAttribute("LABEL", "CSIP");
        metsRoot.addContent(physicalStructMap);

        Element div = new Element("div", metsNamespace);
        div.setAttribute("ID", "uuid-" + UUID.randomUUID().toString());
        div.setAttribute("TYPE", "OTHER");
        div.setAttribute("LABEL", fileGrpType);
        physicalStructMap.addContent(div);

        physSequence.setAttribute("LABEL", "Data");

        div.addContent(physSequence);
        // remove non existing, superfluous files

        // run through fptr and remove fptr for other filegroups
        List<Element> fptrRemoveList = new ArrayList<>();
        List<Element> pageRemoveList = new ArrayList<>();
        int pageNo = 0;
        for (Element page : physSequence.getChildren("div", metsNamespace)) {
            if (pageNo < numberOfFiles) {
                for (Element fptr : page.getChildren("fptr", metsNamespace)) {
                    String fptrId = fptr.getAttributeValue("FILEID");
                    if (!fileIdentifier.contains(fptrId)) {
                        fptrRemoveList.add(fptr);
                    } else if (!fptrId.startsWith("uuid")) {
                        fptr.setAttribute("FILEID", "uuid-" + fptrId);
                    }

                    List<Element> fileSeq = fptr.getChildren("seq", metsNamespace);
                    if (fileSeq != null) {
                        for (Element seq : fileSeq) {
                            for (Element area : seq.getChildren("area", metsNamespace)) {
                                String areaId = area.getAttributeValue("FILEID");
                                if (!areaId.startsWith("uuid")) {
                                    area.setAttribute("FILEID", "uuid-" + areaId);
                                }
                            }
                        }
                    }
                }
            } else {
                pageRemoveList.add(page);
            }
            pageNo++;
        }
        for (Element fptr : fptrRemoveList) {
            fptr.getParent().removeContent(fptr);
        }
        for (Element page : pageRemoveList) {
            physSequence.removeContent(page);
        }
        // finally list all remaining page/area identifier
        List<String> idList = new ArrayList<>();
        for (Element page : physSequence.getChildren("div", metsNamespace)) {
            String pageId = page.getAttributeValue("ID");
            idList.add(pageId);
            for (Element fptr : page.getChildren("fptr", metsNamespace)) {
                List<Element> fileSeq = fptr.getChildren("seq", metsNamespace);
                if (fileSeq != null) {
                    for (Element seq : fileSeq) {
                        for (Element area : seq.getChildren("area", metsNamespace)) {
                            String areaId = area.getAttributeValue("ID");
                            idList.add(areaId);
                        }
                    }
                }
            }
        }
        return idList;
    }

    private void setProjectParameter(String identifier, VariableReplacer vp, MetsModsImportExport exportFilefoExport) {
        exportFilefoExport.setRightsOwner(vp.replace(rightsOwner));
        exportFilefoExport.setRightsOwnerLogo(vp.replace(rightsOwnerLogo));
        exportFilefoExport.setRightsOwnerSiteURL(vp.replace(rightsOwnerSiteURL));
        exportFilefoExport.setRightsOwnerContact(vp.replace(rightsOwnerContact));
        exportFilefoExport.setDigiprovPresentation(vp.replace(digiprovPresentation));
        exportFilefoExport.setDigiprovReference(vp.replace(digiprovReference));
        exportFilefoExport.setDigiprovPresentationAnchor(vp.replace(digiprovPresentationAnchor));
        exportFilefoExport.setDigiprovReferenceAnchor(vp.replace(digiprovReferenceAnchor));

        exportFilefoExport.setMetsRightsLicense(vp.replace(metsRightsLicense));
        exportFilefoExport.setMetsRightsSponsor(vp.replace(metsRightsSponsor));
        exportFilefoExport.setMetsRightsSponsorLogo(vp.replace(metsRightsSponsorLogo));
        exportFilefoExport.setMetsRightsSponsorSiteURL(vp.replace(metsRightsSponsorSiteURL));

        exportFilefoExport.setIIIFUrl(vp.replace(iiifUrl));
        exportFilefoExport.setSruUrl(vp.replace(sruUrl));

        // mets pointer between anchor and volume
        String pointer = project.getMetsPointerPath();
        pointer = vp.replace(pointer);
        exportFilefoExport.setMptrUrl(pointer);

        String anchor = project.getMetsPointerPathAnchor();
        pointer = vp.replace(anchor);
        exportFilefoExport.setMptrAnchorUrl(pointer);

        // obj id -> DOI
        exportFilefoExport.setGoobiID(identifier);
    }

    private Path getSourceFolder(String folder) {
        Path sourceFolder = null;
        try {
            switch (folder) {
                case "master":
                    sourceFolder = Paths.get(process.getImagesOrigDirectory(false));
                    break;
                case "alto":
                    sourceFolder = Paths.get(process.getOcrAltoDirectory());
                    break;
                case "xml":
                    sourceFolder = Paths.get(process.getOcrXmlDirectory());
                    break;
                case "txt":
                    sourceFolder = Paths.get(process.getOcrTxtDirectory());
                    break;
                case "pdf":
                    sourceFolder = Paths.get(process.getOcrPdfDirectory());
                    break;
                case "import":
                    sourceFolder = Paths.get(process.getImportDirectory());
                    break;
                default:
                    sourceFolder = Paths.get(process.getConfiguredImageFolder(folder));
                    break;
            }
        } catch (IOException | SwapException | DAOException e) {
            log.error(e);
        }
        return sourceFolder;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null; // NOSONAR
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    private static void cleanUpNamespacesAndSchemaLocation(Element rootElement) {
        // first find all used namespaces
        List<String> usedPrefixes = new ArrayList<>();
        getUsedNamespaceList(rootElement, usedPrefixes);

        List<Namespace> allNamespaces = rootElement.getAdditionalNamespaces();
        List<Namespace> superfluousNamespaces = new ArrayList<>();
        // run through all namespaces, check if namespace is used
        for (Namespace ns : allNamespaces) {
            if (StringUtils.isNotBlank(ns.getPrefix()) && !usedPrefixes.contains(ns.getPrefix())) {
                superfluousNamespaces.add(ns);
            }
        }
        // remove all unused namespaces
        for (Namespace ns : superfluousNamespaces) {
            rootElement.removeNamespaceDeclaration(ns);
        }

        // build schemaLocation for all remaining namespaces

        StringBuilder sb = new StringBuilder();
        for (String ns : usedPrefixes) {
            switch (ns) {
                case "mets":
                    sb.append(" http://www.loc.gov/METS/ https://www.loc.gov/standards/mets/version112/mets.xsd");
                    break;
                case "premis":
                    sb.append(" http://www.loc.gov/standards/premis/ http://www.loc.gov/standards/premis/v2/premis-v2-0.xsd");
                    break;
                case "mods":
                    sb.append(" http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-7.xsd");
                    break;
                case "mix":
                    sb.append(" http://www.loc.gov/standards/mix/ http://www.loc.gov/standards/mix/mix.xsd");
                    break;
                case "xlink":
                    sb.append(" http://www.w3.org/1999/xlink http://www.loc.gov/standards/xlink/xlink.xsd");
                    break;
                case "csip":
                    sb.append(" https://DILCIS.eu/XML/METS/CSIPExtensionMETS https://earkcsip.dilcis.eu/schema/DILCISExtensionMETS.xsd");
                    break;
                case "sip":
                    sb.append(" https://DILCIS.eu/XML/METS/SIPExtensionMETS https://earksip.dilcis.eu/schema/DILCISExtensionSIPMETS.xsd");
                    break;
                default:
                    break;
            }
        }

        if (sb.length() > 0) {
            rootElement.setAttribute("schemaLocation", sb.toString().trim(), xsiNamespace);
        }
    }

    /**
     * Collect all namespaces from given element and its children
     * 
     * @param element
     * @param prefixList
     */
    private static void getUsedNamespaceList(Element element, List<String> prefixList) {
        String prefix = element.getNamespacePrefix();
        if (StringUtils.isNotBlank(prefix) && !prefixList.contains(prefix)) {
            prefixList.add(prefix);
        }
        for (Attribute attr : element.getAttributes()) {
            Namespace attrNamespace = attr.getNamespace();
            if (attrNamespace != null && StringUtils.isNotBlank(attrNamespace.getPrefix())) {
                String attrPrefix = attrNamespace.getPrefix();
                if (StringUtils.isNotBlank(attrPrefix) && !prefixList.contains(attrPrefix)) {
                    prefixList.add(attrPrefix);
                }
            }
        }

        List<Element> children = element.getChildren();
        if (children != null) {
            for (Element child : children) {
                getUsedNamespaceList(child, prefixList);
            }
        }
    }

    private List<Path> getFolderContent(Path folder) throws IOException {
        List<Path> filesInFolder = new ArrayList<>();
        try (Stream<Path> input = Files.find(folder, 99, (p, bfa) -> bfa.isRegularFile())) {
            input.forEach(filesInFolder::add);
        }
        Collections.sort(filesInFolder);
        return filesInFolder;
    }
}
