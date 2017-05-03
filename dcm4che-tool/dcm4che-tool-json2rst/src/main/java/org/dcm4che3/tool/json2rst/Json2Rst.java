/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2016
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4che3.tool.json2rst;

import javax.json.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2016
 */
public class Json2Rst {

    private static final String UNDERLINE = "===============================================================";
    private final File indir;
    private final File outdir;
    private String tabularColumns = "|p{4cm}|l|p{8cm}|l|";
    private final LinkedList<File> inFiles = new LinkedList<>();
    private final HashSet<String> totRefs = new HashSet<>();

    public Json2Rst(File inFile, File outdir) {
        this.indir = inFile.getParentFile();
        this.outdir = outdir;
        inFiles.add(inFile);
    }

    public void setTabularColumns(String tabularColumns) {
        this.tabularColumns = tabularColumns;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: json2rst <path-to-device.schema.json> <output-dir> [<tabular-columns>]");
            System.exit(-1);
        }
        Json2Rst json2Rst = new Json2Rst(new File(args[0]), new File(args[1]));
        if (args.length > 2)
            json2Rst.setTabularColumns(args[2]);
        json2Rst.process();
    }

    private void process() throws IOException {
        while (!inFiles.isEmpty())
            transform(inFiles.remove());
    }

    private void transform(File inFile) throws IOException {
        String outFileName = inFile.getName().replace(".schema.json", ".rst");
        File outFile = new File(outdir, outFileName);
        System.out.println(inFile + " => " + outFile);
        try (
            InputStreamReader is = new InputStreamReader(new FileInputStream(inFile));
            PrintStream out = new PrintStream(new FileOutputStream(outFile));
        ) {
            JsonReader reader = Json.createReader(is);
            writeTo(reader.readObject(), out, outFileName);
        }
    }

    private void writeTo(JsonObject doc, PrintStream out, String outFileName) throws IOException {
        String title = doc.getString("title");
        writeHeader(doc, out);
        ArrayList<String> refs = new ArrayList<>();
        findRefs(doc, refs);
        if (!refs.isEmpty())
            writeTocTree(refs, out);
        writeAttributesHeader(out, outFileName, title);
        writePropertiesTo(doc, out);
    }

    private void writeHeader(JsonObject doc, PrintStream out) {
        String title = doc.getString("title");
        out.println(title);
        out.println(UNDERLINE.substring(0, title.length()));
        out.println(doc.getString("description"));
    }

    private void findRefs(JsonObject doc, ArrayList<String> refs) {
        JsonObject properties = doc.getJsonObject("properties");
        for (String name : properties.keySet()) {
            JsonObject property = properties.getJsonObject(name);
            JsonObject items = property.getJsonObject("items");
            JsonObject typeObj = items == null ? property : items;
            if (typeObj.containsKey("$ref")) {
                String ref = typeObj.getString("$ref");
                if (totRefs.add(ref)) {
                    refs.add(ref);
                    inFiles.add(new File(indir, ref));
                }
            }
        }
    }

    private void writeAttributesHeader(PrintStream out, String outFileName, String title) {
        out.println();
        out.print(".. tabularcolumns:: ");
        out.println(tabularColumns);
        out.print(".. csv-table:: ");
        out.print(title);
        out.print(" Attributes (LDAP Object: ");
        int endIndex = outFileName.length() - 4;
        if (outFileName.startsWith("hl7"))
            out.print(outFileName.substring(0, endIndex));
        else  if (outFileName.startsWith("id")) {
            out.print("dcmID");
            out.print(outFileName.substring(2, endIndex));
        } else {
            out.print("dcm");
            out.print(Character.toUpperCase(outFileName.charAt(0)));
            out.print(outFileName.substring(1, endIndex));
        }
        out.println(')');
        out.println("    :header: Name, Type, Description, LDAP Attribute");
        out.println("    :widths: 20, 7, 60, 13");
        out.println();
    }

    private void writeTocTree(ArrayList<String> refs, PrintStream out) {
        out.println();
        out.println(".. toctree::");
        out.println();
        for (String ref : refs) {
            out.print("    ");
            out.println(ref.substring(0, ref.length()-12));
        }
    }

    private void writePropertiesTo(JsonObject doc, PrintStream out) throws IOException {
        JsonObject properties = doc.getJsonObject("properties");
        HashSet<String> required = new HashSet<>();
        JsonArray jsonArray = doc.getJsonArray("required");
        if (jsonArray != null)
            for (JsonValue jsonValue : jsonArray) {
                required.add(((JsonString) jsonValue).getString());
            }
        for (String name : properties.keySet()) {
            JsonObject property = properties.getJsonObject(name);
            writePropertyTo(property, required.contains(name), name, out);
        }
    }

    private void writePropertyTo(JsonObject property, boolean required, String name, PrintStream out)
            throws IOException {
        JsonObject items = property.getJsonObject("items");
        JsonObject typeObj = items == null ? property : items;
        out.print("    \"");
        String type;
        if (typeObj.containsKey("$ref")) {
            type = "object";
            String ref = typeObj.getString("$ref");
            out.print(":doc:`");
            out.print(ref.substring(0, ref.length()-12));
            out.print("` ");
            if (items != null) out.print("(s)");
        } else {
            type = typeObj.getString("type");
            if (required) out.print("**");
            out.print(property.getString("title"));
            if (items != null) out.print("(s)");
            if (required) out.print("**");
        }

        out.print("\",");
        out.print(type);
        out.print(",\"");
        out.print(property.getString("description").replace("\"","\"\""));
        out.println("\",\"");
        out.print("    .. _");
        out.print(name);
        out.println(':');
        out.println();
        out.print("    ");
        out.print(name);
        out.println("_\"");
    }
}
