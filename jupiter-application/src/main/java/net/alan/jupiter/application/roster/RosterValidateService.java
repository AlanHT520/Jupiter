package net.alan.jupiter.application.roster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.domain.model.Room;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.domain.repository.RoomRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RosterValidateService {

    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;

    public List<ValidationError> validateStudent(Student student) {
        List<ValidationError> errors = new ArrayList<>();

        if (student.getStudentNo() == null || student.getStudentNo().isBlank()) {
            errors.add(new ValidationError("studentNo", "学号不能为空"));
        }
        if (student.getName() == null || student.getName().isBlank()) {
            errors.add(new ValidationError("name", "姓名不能为空"));
        }
        return errors;
    }

    public List<ValidationError> validateBatchWithBuilding(List<Student> students, String buildingId) {
        List<ValidationError> allErrors = new ArrayList<>();

        List<String> studentNos = students.stream()
                .map(Student::getStudentNo)
                .filter(Objects::nonNull)
                .toList();
        Set<String> existingNos = studentRepository.findByStudentNoIn(studentNos)
                .stream()
                .map(Student::getStudentNo)
                .collect(Collectors.toSet());

        List<String> roomNumbers = students.stream()
                .map(Student::getRoomNumber)
                .filter(Objects::nonNull)
                .toList();
        Set<String> existingRooms = roomNumbers.isEmpty() ? Set.of() :
                roomRepository.findByRoomNumberInAndBuildingId(roomNumbers, buildingId)
                        .stream()
                        .map(Room::getRoomNumber)
                        .collect(Collectors.toSet());

        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            List<ValidationError> errors = new ArrayList<>();

            if (student.getStudentNo() == null || student.getStudentNo().isBlank()) {
                errors.add(new ValidationError("studentNo", "学号不能为空"));
            } else if (existingNos.contains(student.getStudentNo())) {
                errors.add(new ValidationError("studentNo",
                        "学号已存在: " + student.getStudentNo()));
            }
            if (student.getName() == null || student.getName().isBlank()) {
                errors.add(new ValidationError("name", "姓名不能为空"));
            }
            if (student.getRoomNumber() != null && !student.getRoomNumber().isBlank()
                    && !existingRooms.contains(student.getRoomNumber())) {
                errors.add(new ValidationError("roomNumber",
                        "在楼栋 " + buildingId + " 中未找到房间号: " + student.getRoomNumber()));
            }

            for (ValidationError error : errors) {
                error.setRow(i + 1);
                allErrors.add(error);
            }
        }
        return allErrors;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ValidationError {
        private int row;
        private String field;
        private String message;

        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }
}