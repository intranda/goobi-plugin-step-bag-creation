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
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.transfer.SftpUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.exceptions.UGHException;

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

    private String sftpUserName;
    private String sftpPassword;
    private String sftpHostname;
    private String sftpPathToKnownHostsFile;
    private int sftpPort = 22;
    private String sftpRemoteFolder;

    @Override
    public PluginReturnValue run() {
        String identifier = null;
        // open mets file, get doi
        Path tarFile = null;
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
            if (identifier == null) {
                // no doi found, cancel
                return PluginReturnValue.ERROR;
            }

            // check if tar file exists

            tarFile = Paths.get(process.getProcessDataDirectory(), identifier.replace("/", "_") + ".tar");

            if (!StorageProvider.getInstance().isFileExists(tarFile)) {
                // file not found, cancel
                return PluginReturnValue.ERROR;
            }

        } catch (UGHException | IOException | SwapException e) {
            log.error(e);
        }
        // open sftp connection
        try (SftpUtils utils = new SftpUtils(sftpUserName, sftpPassword, sftpHostname, sftpPort, sftpPathToKnownHostsFile)) {
            // upload file
            utils.changeRemoteFolder(sftpRemoteFolder);
            utils.uploadFile(tarFile);

        } catch (JSchException | SftpException e) {
            log.error(e);
            return PluginReturnValue.ERROR;
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
        sftpUserName = myconfig.getString("/sftp/username");
        sftpPassword = myconfig.getString("/sftp/password");
        sftpHostname = myconfig.getString("/sftp/hostname");
        sftpPathToKnownHostsFile = myconfig.getString("/sftp/knownHostsFile");
        sftpPort = myconfig.getInt("/sftp/port");
        sftpRemoteFolder = myconfig.getString("/sftp/remoteFolder");
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
