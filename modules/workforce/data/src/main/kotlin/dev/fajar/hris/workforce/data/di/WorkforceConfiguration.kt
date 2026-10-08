package dev.fajar.hris.workforce.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.data.datasources.*
import dev.fajar.hris.workforce.data.repositories.StoredAttendanceCorrectionRepository
import dev.fajar.hris.workforce.data.repositories.StoredAttendanceRepository
import dev.fajar.hris.workforce.data.repositories.StoredScheduleRepository
import dev.fajar.hris.workforce.domain.repositories.AttendanceCorrectionRepository
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class WorkforceConfiguration {
    @Bean fun shiftSource(sql: DSLContext): ShiftDataSource = PostgresShiftDataSource(sql)

    @Bean fun calendarSource(sql: DSLContext): CalendarDataSource = PostgresCalendarDataSource(sql)

    @Bean fun holidaySource(sql: DSLContext): HolidayDataSource = PostgresHolidayDataSource(sql)

    @Bean
    fun schedules(
        shifts: ShiftDataSource,
        calendars: CalendarDataSource,
        holidays: HolidayDataSource,
        json: ObjectMapper,
    ): ScheduleRepository = StoredScheduleRepository(shifts, calendars, holidays, json)

    @Bean
    fun saveShift(
        schedules: ScheduleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SaveShift(schedules, operations, journal, transactions)

    @Bean
    fun listShifts(schedules: ScheduleRepository, transactions: TransactionRunner) =
        ListShifts(schedules, transactions)

    @Bean
    fun assignSchedule(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AssignWeeklySchedule(schedules, people, operations, journal, transactions)

    @Bean
    fun setRosterDay(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SetRosterDay(schedules, people, operations, journal, transactions)

    @Bean
    fun employeeCalendar(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetEmployeeCalendar(schedules, people, transactions, clock)

    @Bean
    fun saveHoliday(
        schedules: ScheduleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SaveWorkHoliday(schedules, operations, journal, transactions)

    @Bean
    fun listHolidays(schedules: ScheduleRepository, transactions: TransactionRunner) =
        ListWorkHolidays(schedules, transactions)

    @Bean
    fun attendanceSource(sql: DSLContext): AttendanceDataSource = PostgresAttendanceDataSource(sql)

    @Bean
    fun attendance(source: AttendanceDataSource, json: ObjectMapper): AttendanceRepository =
        StoredAttendanceRepository(source, json)

    @Bean
    fun issueAttendanceWindow(
        attendance: AttendanceRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        operations: OperationRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = IssueAttendanceCaptureWindow(attendance, people, companies, operations, transactions, clock)

    @Bean
    fun recordAttendance(
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        RecordAttendance(
            attendance,
            corrections,
            schedules,
            people,
            companies,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun reviewAttendance(
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ReviewAttendance(attendance, corrections, people, operations, journal, transactions, clock)

    @Bean
    fun employeeAttendance(
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetEmployeeAttendance(attendance, corrections, people, transactions, clock)

    @Bean
    fun attendanceCorrectionSource(sql: DSLContext): AttendanceCorrectionDataSource =
        PostgresAttendanceCorrectionDataSource(sql)

    @Bean
    fun attendanceCorrections(
        source: AttendanceCorrectionDataSource,
        json: ObjectMapper,
    ): AttendanceCorrectionRepository = StoredAttendanceCorrectionRepository(source, json)

    @Bean
    fun correctAttendance(
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        CorrectAttendance(
            attendance,
            corrections,
            schedules,
            people,
            companies,
            operations,
            journal,
            transactions,
            clock,
        )

    @Bean
    fun correctionHistory(
        corrections: AttendanceCorrectionRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetAttendanceCorrectionHistory(corrections, people, transactions, clock)
}
