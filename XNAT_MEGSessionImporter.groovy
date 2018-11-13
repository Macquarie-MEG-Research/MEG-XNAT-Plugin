import org.nrg.mqmeg.importer.MegImporter

Map<String, Object> map = new HashMap<String, Object>();
map.put("project", project)
map.put("BuildPath", BuildPath)

def uploader = new MegImporter(user, subject, map)
try {
    def listout = uploader.call();
    listout.each {
        println it;
    }
} catch (Exception e) {
    println e
}