package net.alan.jupiter.infrastructure.excel;

public final class ProofPathResolver {

    private ProofPathResolver() {}

    public static final String PROOF_ROOT = "请假截图";
    public static final String BUILDING_SUB = "楼栋";

    public static String buildFileName(String roomNumber, String studentNo,
                                        String studentName, String statusLabel,
                                        String ext) {
        String room = sanitize(roomNumber == null || roomNumber.isBlank()
                || "—".equals(roomNumber) ? "未分房" : roomNumber);
        String no   = sanitize(studentNo);
        String name = sanitize(studentName);
        String st   = (statusLabel == null || statusLabel.isBlank()) ? "未知" : statusLabel;
        String ex   = (ext == null || ext.isBlank()) ? ".jpg"
                : (ext.startsWith(".") ? ext : "." + ext);
        return room + "_" + no + "_" + name + "_" + st + ex;
    }

    public static String buildingDir(String buildingName) {
        return sanitize(buildingName);
    }

    public static String floorDir(String floor) {
        if (floor == null || floor.isBlank()) return "未分房";
        if (floor.matches("\\d+")) return floor + "层";
        return sanitize(floor);
    }

    public static String roomDir(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank() || "—".equals(roomNumber)) {
            return "未分房";
        }
        return sanitize(roomNumber);
    }

    public static String prefixFromBuildingSummary() {
        return "../../" + PROOF_ROOT + "/" + BUILDING_SUB + "/";
    }

    public static String prefixFromBuildingFloor() {
        return "../../../" + PROOF_ROOT + "/" + BUILDING_SUB + "/";
    }

    public static String prefixFromClassReport() {
        return "../" + PROOF_ROOT + "/" + BUILDING_SUB + "/";
    }

    public static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}