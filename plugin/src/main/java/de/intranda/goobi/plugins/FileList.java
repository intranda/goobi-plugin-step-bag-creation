package de.intranda.goobi.plugins;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileList {

    private String fileGroupName;

    private Path sourceFolder;

    private List<Path> files = new ArrayList<>();

}
