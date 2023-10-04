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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.SwapException;
import io.goobi.workflow.api.connection.FtpUtils;
import io.goobi.workflow.api.connection.SftpUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.exceptions.UGHException;

@PluginImplementation
@Log4j2
public class BagSubmissionStepPlugin implements IStepPluginVersion2 {

    private static final long serialVersionUID = 2812883682084780289L;
    @Getter
    private String title = "intranda_step_bagsubmission";
    @Getter
    private Step step;

    @Getter
    private Process process;

    @Getter
    private String returnPath;

    private String userName;
    private String password;
    private String sftpKeyfile;
    private String hostname;
    private int port = 22;
    private String sftpPathToKnownHostsFile;
    private String sftpRemoteFolder;
    private String localFolder;

    private String connectionType;

    @Override
    public PluginReturnValue run() {
        String identifier = null;
        Path tarFile = null;
        // open mets file, get doi
        try {
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
                // no identifier found, cancel
                return PluginReturnValue.ERROR;
            }

            // check if tar file exists

            tarFile = Paths.get(process.getProcessDataDirectory(), identifier.replace("/", "_") + "_bag.tar");
            //  rename file before upload
            if (!StorageProvider.getInstance().isFileExists(tarFile)) {
                // file not found, cancel
                return PluginReturnValue.ERROR;
            }

        } catch (UGHException | IOException | SwapException e) {
            log.error(e);
        }
        if (StringUtils.isNotBlank(localFolder)) {

            Path destination = Paths.get(localFolder, tarFile.getFileName().toString());
            try {
                StorageProvider.getInstance().copyFile(tarFile, destination);
            } catch (IOException e) {
                log.error(e);
                return PluginReturnValue.ERROR;
            }

        } else if ("ftp".equalsIgnoreCase(connectionType)) {
            try (FtpUtils connection = new FtpUtils(userName, password, hostname, port)) {
                // upload file
                connection.changeRemoteFolder(sftpRemoteFolder);
                connection.uploadFile(tarFile);
            } catch (Exception e) {
                log.error(e);
                return PluginReturnValue.ERROR;
            }
        } else if (StringUtils.isBlank(sftpKeyfile)) {
            try (SftpUtils connection = new SftpUtils(userName, password, hostname, port, sftpPathToKnownHostsFile)) {
                // upload file
                connection.changeRemoteFolder(sftpRemoteFolder);
                connection.uploadFile(tarFile);
            } catch (Exception e) {
                log.error(e);
                return PluginReturnValue.ERROR;
            }
        } else {
            try (SftpUtils connection = new SftpUtils(userName, sftpKeyfile, password, hostname, port, sftpPathToKnownHostsFile)) {
                // upload file
                connection.changeRemoteFolder(sftpRemoteFolder);
                connection.uploadFile(tarFile);
            } catch (Exception e) {
                log.error(e);
                return PluginReturnValue.ERROR;
            }
        }
        // delete local zip file
        try {
            StorageProvider.getInstance().deleteFile(tarFile);
        } catch (IOException e) {
            log.error(e);
        }

        return PluginReturnValue.FINISH;
    }

    @Override
    public String cancel() {
        return null;
    }

    @Override
    public String finish() {
        return null;
    }

    @Override
    public String getPagePath() {
        return null;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        process = step.getProzess();

        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig("intranda_step_bagcreation", step);

        connectionType = myconfig.getString("/connection/type");
        userName = myconfig.getString("/connection/username");
        password = myconfig.getString("/connection/password");
        sftpKeyfile = myconfig.getString("/connection/keyfile");
        hostname = myconfig.getString("/connection/hostname");
        sftpPathToKnownHostsFile = myconfig.getString("/connection/knownHostsFile");
        port = myconfig.getInt("/connection/port");
        sftpRemoteFolder = myconfig.getString("/connection/remoteFolder");

        localFolder = myconfig.getString("/exportFolder", null);
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
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

}
