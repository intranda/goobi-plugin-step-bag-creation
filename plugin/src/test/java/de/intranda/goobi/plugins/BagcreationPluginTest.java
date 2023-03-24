package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.MatchResult;

import org.easymock.EasyMock;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.beans.User;
import org.goobi.production.enums.PluginReturnValue;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.JwtHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.XmlTools;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MetadatenHelper.class, VariableReplacer.class, ConfigurationHelper.class, ProcessManager.class, MetadataManager.class,
        JwtHelper.class })

@PowerMockIgnore({ "javax.management.*", "javax.xml.*", "org.xml.*", "org.w3c.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
public class BagcreationPluginTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static String resourcesFolder;

    private File processDirectory;
    private File metadataDirectory;
    private Process process;
    private Step step;
    private Prefs prefs;

    private static final Namespace metsNamespace = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");
    private static final Namespace modsNamespace = Namespace.getNamespace("mods", "http://www.loc.gov/mods/v3");

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Test
    public void testConstructor() throws Exception {
        BagcreationStepPlugin plugin = new BagcreationStepPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testInit() {
        BagcreationStepPlugin plugin = new BagcreationStepPlugin();
        plugin.initialize(step, "something");
        assertEquals("something", plugin.getReturnPath());
        assertEquals(step.getTitel(), plugin.getStep().getTitel());

    }

    @Test
    public void testRun() throws Exception {
        BagcreationStepPlugin plugin = new BagcreationStepPlugin();
        plugin.initialize(step, "something");
        PluginReturnValue answer = plugin.run();
        assertEquals(PluginReturnValue.FINISH, answer);
        String metsfile = plugin.getTempfolder().toString() + "/METS.xml";

        assertTrue(Files.exists(Paths.get(metsfile)));

        Document doc = XmlTools.getSAXBuilder().build(metsfile);
        Element mets = doc.getRootElement();
        assertEquals("10.33510/nls.js.1511270477762", mets.getAttributeValue("OBJID"));

        Path descriptiveMetadataFolder = Paths.get(plugin.getTempfolder().toString(), "metadata/descriptive");
        // created 4 files for DMDLOG_0001 to DMDLOG_0001
        assertEquals(4, StorageProvider.getInstance().listFiles(descriptiveMetadataFolder.toString()).size());

        Element fileSec = mets.getChild("fileSec", metsNamespace);
        assertEquals(2, fileSec.getChildren().size());

    }

    @Before
    public void setUp() throws Exception {
        File tempdir = folder.newFolder("tmp");
        tempdir.mkdirs();
        metadataDirectory = folder.newFolder("metadata");
        processDirectory = new File(metadataDirectory + File.separator + "1");
        processDirectory.mkdirs();
        String metadataDirectoryName = metadataDirectory.getAbsolutePath() + File.separator;
        Path metaSource = Paths.get(resourcesFolder, "meta.xml");
        Path metaTarget = Paths.get(processDirectory.getAbsolutePath(), "meta.xml");
        Files.copy(metaSource, metaTarget);

        Path anchorSource = Paths.get(resourcesFolder, "meta_anchor.xml");
        Path anchorTarget = Paths.get(processDirectory.getAbsolutePath(), "meta_anchor.xml");
        Files.copy(anchorSource, anchorTarget);

        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1800000l).anyTimes();
        EasyMock.expect(configurationHelper.isAllowWhitespacesInFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isUseProxy()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiContentServerTimeOut()).andReturn(60000).anyTimes();
        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(metadataDirectoryName).anyTimes();
        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getConfigurationFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getTemporaryFolder()).andReturn(tempdir.getAbsolutePath()).anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMainDirectoryName()).andReturn("processtitle_media").anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMasterDirectoryName()).andReturn("master_processtitle_media").anyTimes();
        EasyMock.expect(configurationHelper.getProcessOcrTxtDirectoryName()).andReturn("processtitle_txt").anyTimes();
        EasyMock.expect(configurationHelper.getProcessOcrXmlDirectoryName()).andReturn("processtitle_xml").anyTimes();
        EasyMock.expect(configurationHelper.getProcessOcrPdfDirectoryName()).andReturn("processtitle_pdf").anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesSourceDirectoryName()).andReturn("processtitle_source").anyTimes();
        EasyMock.expect(configurationHelper.getProcessImportDirectoryName()).andReturn("import").anyTimes();
        EasyMock.expect(configurationHelper.getScriptsFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiFolder()).andReturn(resourcesFolder).anyTimes();

        EasyMock.expect(configurationHelper.isUseMasterDirectory()).andReturn(true).anyTimes();
        EasyMock.expect(configurationHelper.isCreateMasterDirectory()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isCreateSourceFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getNumberOfMetaBackups()).andReturn(0).anyTimes();

        PowerMock.mockStatic(JwtHelper.class);
        EasyMock.expect(JwtHelper.createApiToken(EasyMock.anyString(), EasyMock.anyObject())).andReturn("12356").anyTimes();
        PowerMock.replay(JwtHelper.class);
        EasyMock.expect(configurationHelper.getGoobiUrl()).andReturn("").anyTimes();

        EasyMock.replay(configurationHelper);

        PowerMock.mockStatic(VariableReplacer.class);
        EasyMock.expect(VariableReplacer.simpleReplace(EasyMock.anyString(), EasyMock.anyObject())).andReturn("master_processtitle_media");
        EasyMock.expect(VariableReplacer.simpleReplace(EasyMock.anyString(), EasyMock.anyObject())).andReturn("processtitle_xml");
        EasyMock.expect(VariableReplacer.simpleReplace(EasyMock.anyString(), EasyMock.anyObject())).andReturn("").anyTimes();
        List<MatchResult> results = new ArrayList<>();
        EasyMock.expect(VariableReplacer.findRegexMatches(EasyMock.anyString(), EasyMock.anyString())).andReturn(results).anyTimes();

        PowerMock.replay(VariableReplacer.class);
        prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        Fileformat ff = new MetsMods(prefs);
        ff.read(metaTarget.toString());

        PowerMock.mockStatic(MetadatenHelper.class);
        EasyMock.expect(MetadatenHelper.getMetaFileType(EasyMock.anyString())).andReturn("mets").anyTimes();
        EasyMock.expect(MetadatenHelper.getFileformatByName(EasyMock.anyString(), EasyMock.anyObject())).andReturn(ff).anyTimes();
        EasyMock.expect(MetadatenHelper.getMetadataOfFileformat(EasyMock.anyObject(), EasyMock.anyBoolean()))
                .andReturn(Collections.emptyMap())
                .anyTimes();
        PowerMock.replay(MetadatenHelper.class);

        PowerMock.mockStatic(MetadataManager.class);
        MetadataManager.updateMetadata(1, Collections.emptyMap());
        MetadataManager.updateJSONMetadata(1, Collections.emptyMap());
        PowerMock.replay(MetadataManager.class);
        PowerMock.replay(ConfigurationHelper.class);

        process = getProcess();

        Ruleset ruleset = PowerMock.createMock(Ruleset.class);
        ruleset.setTitel("ruleset");
        ruleset.setDatei("ruleset.xml");
        EasyMock.expect(ruleset.getDatei()).andReturn("ruleset.xml").anyTimes();
        process.setRegelsatz(ruleset);
        EasyMock.expect(ruleset.getPreferences()).andReturn(prefs).anyTimes();
        PowerMock.replay(ruleset);

    }

    public Process getProcess() {
        Project project = new Project();
        project.setTitel("SampleProject");

        Process process = new Process();
        process.setTitel("00469418X");
        process.setProjekt(project);
        process.setId(1);

        List<Step> steps = new ArrayList<>();
        step = new Step();
        step.setReihenfolge(1);
        step.setProzess(process);
        step.setTitel("test step");
        step.setBearbeitungsstatusEnum(StepStatus.OPEN);

        User user = new User();
        user.setVorname("Firstname");
        user.setNachname("Lastname");
        step.setBearbeitungsbenutzer(user);
        steps.add(step);

        process.setSchritte(steps);

        try {
            createProcessDirectory(processDirectory);
        } catch (IOException e) {
        }

        return process;
    }

    private void createProcessDirectory(File processDirectory) throws IOException {

        // image folder
        File imageDirectory = new File(processDirectory.getAbsolutePath(), "images");
        imageDirectory.mkdir();
        // master folder
        File masterDirectory = new File(imageDirectory.getAbsolutePath(), "master_processtitle_media");
        masterDirectory.mkdir();

        // media folder
        File mediaDirectory = new File(imageDirectory.getAbsolutePath(), "processtitle_media");
        mediaDirectory.mkdir();

        // ocr folder
        File ocrDirectory = new File(processDirectory.getAbsolutePath(), "ocr");
        ocrDirectory.mkdir();
        File altoDirectory = new File(ocrDirectory.getAbsolutePath(), "processtitle_xml");
        altoDirectory.mkdir();

        // create test files
        Path sourceImageFile = Paths.get(resourcesFolder, "sample.tif");
        createFiles(sourceImageFile, mediaDirectory.toPath(), "tif");
        createFiles(sourceImageFile, masterDirectory.toPath(), "tif");

        Path sourceAltoFile = Paths.get(resourcesFolder, "alto.xml");
        createFiles(sourceAltoFile, altoDirectory.toPath(), "xml");
    }

    private void createFiles(Path sourceFile, Path imageDirectory, String extension) {
        try {
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000001." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000002." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000003." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000004." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000005." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000006." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000007." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000008." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000009." + extension));
            Files.copy(sourceFile, Paths.get(imageDirectory.toString(), "00000010." + extension));
        } catch (IOException e) {

        }
    }

}
