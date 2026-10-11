package net.alan.jupiter.application.roster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.*;
import net.alan.jupiter.infrastructure.excel.ExcelImportHelper;
import net.alan.jupiter.domain.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportClassRosterService {

    private static final List<String> TEMPLATE_HEADERS = List.of(
            "班级", "姓名", "性别", "学号", "宿舍楼", "楼层", "房间号", "床位号", "手机号", "状态", "备注"
    );

    private static final List<String> SAMPLE_ROW = List.of(
            "机车2602", "张浩天", "男", "2602531414", "B4", "4", "408", "1", "13800001111", "在校", ""
    );

    private static final List<List<String>> INSTRUCTION_ROWS = List.of(
            List.of("班级", "必填", "文本", "机车2602", "不存在则自动创建"),
            List.of("姓名", "必填", "文本", "张浩天", ""),
            List.of("性别", "必填", "文本", "男", "男 / 女"),
            List.of("学号", "必填", "文本", "2602531414", "唯一标识，必须设成文本格式"),
            List.of("宿舍楼", "必填", "文本", "B4", "如 B4，不存在则自动创建"),
            List.of("楼层", "必填", "整数", "4", "不存在则自动创建"),
            List.of("房间号", "必填", "文本", "408", "不存在则自动创建"),
            List.of("床位号", "选填", "文本", "1", ""),
            List.of("手机号", "选填", "文本", "13800001111", "11 位数字"),
            List.of("状态", "选填", "文本", "在校", "在校/征兵/实习/走读/长期病假"),
            List.of("备注", "选填", "文本", "", "学生档案备注")
    );

    private final StudentRepository studentRepository;
    private final ClassInfoRepository classInfoRepository;
    private final RoomRepository roomRepository;
    private final DormBuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final ExcelImportHelper excelImportHelper;

    public byte[] downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet importSheet = workbook.createSheet("导入模板");
            Row headerRow = importSheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
                headerRow.createCell(i).setCellValue(TEMPLATE_HEADERS.get(i));
            }
            Row sampleRow = importSheet.createRow(1);
            for (int i = 0; i < SAMPLE_ROW.size(); i++) {
                sampleRow.createCell(i).setCellValue(SAMPLE_ROW.get(i));
            }
            autoSizeSheet(importSheet, TEMPLATE_HEADERS.size());

            Sheet instructionSheet = workbook.createSheet("填写说明");
            Row insHeader = instructionSheet.createRow(0);
            String[] insHeaders = {"列", "是否必填", "数据类型", "示例值", "说明"};
            for (int i = 0; i < insHeaders.length; i++) {
                insHeader.createCell(i).setCellValue(insHeaders[i]);
            }
            for (int r = 0; r < INSTRUCTION_ROWS.size(); r++) {
                Row row = instructionSheet.createRow(r + 1);
                List<String> cells = INSTRUCTION_ROWS.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c).setCellValue(cells.get(c));
                }
            }
            autoSizeSheet(instructionSheet, insHeaders.length);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成模板失败");
        }
    }

    private void autoSizeSheet(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(sheet.getColumnWidth(i) + 512, 15 * 256);
            sheet.setColumnWidth(i, width);
        }
    }

    public List<Student> previewImport(byte[] excelData) {
        return excelImportHelper.parseClassRoster(excelData);
    }

    @Transactional
    public ImportResult importClassRoster(byte[] excelData, String fileName) {
        List<Student> students = excelImportHelper.parseClassRoster(excelData);
        ImportResult result = new ImportResult();

        // 先校验所有行，避免部分导入后失败导致数据丢失
        List<Student> validStudents = new ArrayList<>();
        for (Student student : students) {
            if (student.getStudentNo() == null || student.getStudentNo().isBlank()) {
                result.addError("学号为空，跳过该行");
                continue;
            }
            if (student.getName() == null || student.getName().isBlank()) {
                result.addError("姓名为空，跳过该行，学号: " + student.getStudentNo());
                continue;
            }
            if (student.getClassName() == null || student.getClassName().isBlank()) {
                result.addError("班级为空，跳过该行，学号: " + student.getStudentNo());
                continue;
            }
            validStudents.add(student);
        }

        if (validStudents.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "没有可导入的有效数据，请检查Excel内容");
        }

        checkRecordRepository.clearStudentReferences();
        studentRepository.deleteAll();
        studentRepository.flush();

        Map<String, ClassInfo> classInfoCache = new HashMap<>();
        Map<String, DormBuilding> buildingCache = new HashMap<>();
        Map<String, Floor> floorCache = new HashMap<>();
        Map<String, Room> roomCache = buildRoomCache();
        List<Student> toSave = new ArrayList<>();

        for (Student student : validStudents) {
            resolveAssociations(student, classInfoCache, buildingCache, floorCache, roomCache);
            toSave.add(student);
        }

        studentRepository.saveAll(toSave);
        result.setImported(toSave.size());
        return result;
    }

    private Map<String, Room> buildRoomCache() {
        return roomRepository.findAll().stream()
                .collect(Collectors.toMap(
                        r -> {
                            String bName = r.getBuilding() != null ? r.getBuilding().getName() : "";
                            String fNum = r.getFloor() != null ? String.valueOf(r.getFloor().getFloorNumber()) : "";
                            return bName + "|" + fNum + "|" + r.getRoomNumber();
                        },
                        r -> r,
                        (a, b) -> a));
    }

    private void resolveAssociations(Student student,
                                     Map<String, ClassInfo> classInfoCache,
                                     Map<String, DormBuilding> buildingCache,
                                     Map<String, Floor> floorCache,
                                     Map<String, Room> roomCache) {
        resolveClass(student, classInfoCache);
        resolveBuilding(student, buildingCache);
        resolveFloorAndRoom(student, buildingCache, floorCache, roomCache);
    }

    private void resolveClass(Student student, Map<String, ClassInfo> classInfoCache) {
        String className = student.getClassName();
        if (className == null || className.isBlank()) {
            return;
        }
        ClassInfo classInfo = classInfoCache.computeIfAbsent(className, cn -> {
            ClassInfo ci = classInfoRepository.findByName(cn).orElse(null);
            if (ci == null) {
                ci = new ClassInfo();
                ci.setName(cn);
                ci = classInfoRepository.save(ci);
            }
            return ci;
        });
        student.setClassInfo(classInfo);
        student.setClassName(classInfo.getName());
    }

    private void resolveBuilding(Student student, Map<String, DormBuilding> buildingCache) {
        String buildingName = student.getBuildingName();
        if (buildingName == null || buildingName.isBlank()) {
            return;
        }
        DormBuilding building = buildingCache.computeIfAbsent(buildingName, bn -> {
            DormBuilding b = buildingRepository.findByName(bn).orElse(null);
            if (b == null) {
                b = new DormBuilding();
                b.setName(bn);
                b.setBuildingCode(bn);
                b.setIsActive(true);
                b = buildingRepository.save(b);
            }
            return b;
        });
        student.setBuilding(building);
        student.setBuildingName(building.getName());
    }

    private void resolveFloorAndRoom(Student student,
                                     Map<String, DormBuilding> buildingCache,
                                     Map<String, Floor> floorCache,
                                     Map<String, Room> roomCache) {
        String buildingName = student.getBuildingName();
        String roomNumber = student.getRoomNumber();

        if (buildingName == null || buildingName.isBlank()) {
            return;
        }

        DormBuilding building = buildingCache.get(buildingName);
        if (building == null) {
            return;
        }

        String floorKey = building.getName() + "|"
                + (student.getFloorNumber() != null ? student.getFloorNumber() : 0);
        Floor floor = floorCache.computeIfAbsent(floorKey, fk -> {
            Floor f = floorRepository
                    .findByBuildingIdAndFloorNumber(building.getId(), student.getFloorNumber())
                    .orElse(null);
            if (f == null) {
                f = new Floor();
                f.setBuilding(building);
                f.setFloorNumber(student.getFloorNumber());
                f.setFloorName((student.getFloorNumber() != null ? student.getFloorNumber() : 0) + "层");
                f = floorRepository.save(f);
            }
            return f;
        });

        if (roomNumber == null || roomNumber.isBlank()) {
            return;
        }

        String roomKey = building.getName() + "|"
                + (floor.getFloorNumber() != null ? floor.getFloorNumber() : 0) + "|"
                + roomNumber;
        Room room = roomCache.computeIfAbsent(roomKey, rk -> {
            Room r = roomRepository.findByRoomNumberAndBuildingId(roomNumber, building.getId())
                    .orElse(null);
            if (r == null) {
                r = new Room();
                r.setBuilding(building);
                r.setRoomNumber(roomNumber);
                r.setFloor(floor);
                r.setIsActive(true);
                r = roomRepository.save(r);
            }
            return r;
        });

        student.setRoom(room);
        student.setRoomNumber(room.getRoomNumber());
        student.setFloorNumber(floor.getFloorNumber());
    }

    @lombok.Data
    public static class ImportResult {
        private int imported;
        private int updated;
        private int skipped;
        private final List<String> skippedNos = new ArrayList<>();
        private final List<String> updatedNos = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public void addSkipped(String studentNo) {
            skipped++;
            skippedNos.add(studentNo);
        }

        public void addUpdated(String studentNo) {
            updated++;
            updatedNos.add(studentNo);
        }

        public void addError(String message) {
            errors.add(message);
        }
    }
}