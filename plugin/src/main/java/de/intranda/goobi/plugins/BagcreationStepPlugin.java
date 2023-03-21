package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

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

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.export.download.ExportMets;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.dl.VirtualFileGroup;
import ugh.exceptions.UGHException;
import ugh.fileformats.mets.MetsModsImportExport;

@PluginImplementation
@Log4j2
public class BagcreationStepPlugin extends ExportMets implements IStepPluginVersion2 {

    private static final long serialVersionUID = 211912948222450125L;
    @Getter
    private String title = "intranda_step_bagcreation";
    @Getter
    private Step step;

    private Process process;

    private Project project;

    private Prefs prefs;

    @Getter
    private String value;
    @Getter
    private String returnPath;

    private List<ProjectFileGroup> filegroups = new ArrayList<>();

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        process = step.getProzess();
        project = process.getProjekt();
        prefs = process.getRegelsatz().getPreferences();

        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        List<HierarchicalConfiguration> grps = myconfig.configurationsAt("/group");
        for (HierarchicalConfiguration hc : grps) {
            ProjectFileGroup group = new ProjectFileGroup();
            group.setName(hc.getString("@fileGrpName"));
            group.setPath(hc.getString("@prefix"));
            group.setMimetype(hc.getString("@mimeType"));
            group.setSuffix(hc.getString("@suffix"));
            group.setFolder(hc.getString("@folder"));
            filegroups.add(group);
        }
    }

    @Override
    public PluginReturnValue run() {
        String identifier = null;
        VariableReplacer vp = null;

        try {
            // read metadata
            Fileformat fileformat = process.readMetadataFile();

            // find doi metadata
            DocStruct ds = fileformat.getDigitalDocument().getLogicalDocStruct();
            if (ds.getType().isAnchor()) {
                ds = ds.getAllChildren().get(0);
            }
            for (Metadata md : ds.getAllMetadata()) {
                if ("DOI".equals(md.getType().getName())) {
                    identifier = md.getValue();
                    break;
                }
            }
            if (identifier == null) {
                // no doi found, cancel export
                return PluginReturnValue.ERROR;
            }
            vp = new VariableReplacer(fileformat.getDigitalDocument(), prefs, process, null);
            // create export file

            MetsModsImportExport exportFilefoExport = new MetsModsImportExport(prefs);
            exportFilefoExport.setDigitalDocument(fileformat.getDigitalDocument());
            exportFilefoExport.setWriteLocal(false);

            // project parameter
            exportFilefoExport.setRightsOwner(vp.replace(project.getMetsRightsOwner()));
            exportFilefoExport.setRightsOwnerLogo(vp.replace(project.getMetsRightsOwnerLogo()));
            exportFilefoExport.setRightsOwnerSiteURL(vp.replace(project.getMetsRightsOwnerSite()));
            exportFilefoExport.setRightsOwnerContact(vp.replace(project.getMetsRightsOwnerMail()));
            exportFilefoExport.setDigiprovPresentation(vp.replace(project.getMetsDigiprovPresentation()));
            exportFilefoExport.setDigiprovReference(vp.replace(project.getMetsDigiprovReference()));
            exportFilefoExport.setDigiprovPresentationAnchor(vp.replace(project.getMetsDigiprovPresentationAnchor()));
            exportFilefoExport.setDigiprovReferenceAnchor(vp.replace(project.getMetsDigiprovReferenceAnchor()));

            exportFilefoExport.setMetsRightsLicense(vp.replace(project.getMetsRightsLicense()));
            exportFilefoExport.setMetsRightsSponsor(vp.replace(project.getMetsRightsSponsor()));
            exportFilefoExport.setMetsRightsSponsorLogo(vp.replace(project.getMetsRightsSponsorLogo()));
            exportFilefoExport.setMetsRightsSponsorSiteURL(vp.replace(project.getMetsRightsSponsorSiteURL()));

            exportFilefoExport.setIIIFUrl(vp.replace(project.getMetsIIIFUrl()));
            exportFilefoExport.setSruUrl(vp.replace(project.getMetsSruUrl()));
            exportFilefoExport.setPurlUrl(vp.replace(project.getMetsPurl()));
            exportFilefoExport.setContentIDs(vp.replace(project.getMetsContentIDs()));

            // mets pointer between anchor and volume
            String pointer = project.getMetsPointerPath();
            pointer = vp.replace(pointer);
            exportFilefoExport.setMptrUrl(pointer);

            String anchor = project.getMetsPointerPathAnchor();
            pointer = vp.replace(anchor);
            exportFilefoExport.setMptrAnchorUrl(pointer);

            // obj id -> DOI
            exportFilefoExport.setGoobiID(identifier);

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
                if (StorageProvider.getInstance().isFileExists(sourceFolder)) {
                    // generate filegroup
                    VirtualFileGroup virt = new VirtualFileGroup(projectFileGroup.getName(), projectFileGroup.getPath(), projectFileGroup.getMimetype(),
                            projectFileGroup.getSuffix());
                    exportFilefoExport.getDigitalDocument().getFileSet().addVirtualFileGroup(virt);
                }
            }
            // save file
            exportFilefoExport.write(process.getMetadataFilePath().replace("meta.xml", "export.xml"));
        } catch (UGHException | IOException | SwapException e) {
            log.error(e);
        }

        // collect data from folders

        // create checksums + payload + size + creation date for each file in all folders/filegroups

        // open exported file, enhance it

        // extract descriptive metadata

        // extract physical data, add checksums

        // generate zip/tar/whatever

        // cleanup temporary files

        return PluginReturnValue.FINISH;
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
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

}
