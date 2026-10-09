package dev.fajar.hris.workforce.data.di

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.data.datasources.*
import dev.fajar.hris.workforce.data.repositories.StoredAttendanceCorrectionRepository
import dev.fajar.hris.workforce.data.repositories.StoredAttendanceRepository
import dev.fajar.hris.workforce.data.repositories.StoredOvertimeRepository
import dev.fajar.hris.workforce.data.repositories.StoredScheduleRepository
import dev.fajar.hris.workforce.data.repositories.StoredWorkPeriodRepository
import dev.fajar.hris.workforce.domain.repositories.AttendanceCorrectionRepository
import dev.fajar.hris.workforce.domain.repositories.AttendanceRepository
import dev.fajar.hris.workforce.domain.repositories.OvertimeRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
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
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = SaveShift(schedules, operations, journal, companies, members, identities, transactions)

    @Bean
    fun listShifts(
        schedules: ScheduleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListShifts(schedules, companies, members, identities, transactions)

    @Bean
    fun assignSchedule(
        periods: WorkPeriodRepository,
        schedules: ScheduleRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) =
        AssignWeeklySchedule(
            periods,
            schedules,
            people,
            operations,
            journal,
            companies,
            members,
            identities,
            transactions,
        )

    @Bean
    fun setRosterDay(
        periods: WorkPeriodRepository,
        schedules: ScheduleRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) =
        SetRosterDay(
            periods,
            schedules,
            people,
            operations,
            journal,
            companies,
            members,
            identities,
            transactions,
        )

    @Bean
    fun employeeCalendar(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetEmployeeCalendar(schedules, people, companies, members, identities, transactions, clock)

    @Bean
    fun saveHoliday(
        periods: WorkPeriodRepository,
        schedules: ScheduleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) =
        SaveWorkHoliday(
            periods,
            schedules,
            operations,
            journal,
            companies,
            members,
            identities,
            transactions,
        )

    @Bean
    fun listHolidays(
        schedules: ScheduleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
    ) = ListWorkHolidays(schedules, companies, members, identities, transactions)

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
        periods: WorkPeriodRepository,
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
            periods,
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
        periods: WorkPeriodRepository,
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        ReviewAttendance(
            periods,
            attendance,
            corrections,
            people,
            operations,
            journal,
            transactions,
            clock,
        )

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
        periods: WorkPeriodRepository,
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
            periods,
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

    @Bean
    fun workPeriodSource(sql: DSLContext): WorkPeriodDataSource = PostgresWorkPeriodDataSource(sql)

    @Bean
    fun workPeriods(source: WorkPeriodDataSource, json: ObjectMapper): WorkPeriodRepository =
        StoredWorkPeriodRepository(source, json)

    @Bean
    fun startWorkPeriodClose(
        periods: WorkPeriodRepository,
        jobs: JobRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        schedules: ScheduleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        overtime: OvertimeRepository,
        identities: IdentityRepository,
        members: MembershipRepository,
    ) =
        StartWorkPeriodClose(
            periods,
            jobs,
            people,
            companies,
            schedules,
            operations,
            journal,
            transactions,
            clock,
            overtime,
            identities,
            members,
        )

    @Bean
    fun advanceWorkPeriodClose(
        periods: WorkPeriodRepository,
        jobs: JobRepository,
        schedules: ScheduleRepository,
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        overtime: OvertimeRepository,
    ) =
        AdvanceWorkPeriodClose(
            periods,
            jobs,
            schedules,
            attendance,
            corrections,
            journal,
            transactions,
            clock,
            overtime,
        )

    @Bean
    fun abortWorkPeriodClose(
        periods: WorkPeriodRepository,
        jobs: JobRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = AbortWorkPeriodClose(periods, jobs, journal, transactions)

    @Bean
    fun recoverWorkPeriod(
        periods: WorkPeriodRepository,
        jobs: JobRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = RecoverWorkPeriod(periods, jobs, operations, journal, transactions)

    @Bean
    fun listWorkPeriods(periods: WorkPeriodRepository, transactions: TransactionRunner) =
        ListWorkPeriods(periods, transactions)

    @Bean
    fun getWorkPeriodSnapshot(
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = GetWorkPeriodSnapshot(periods, people, transactions, clock)

    @Bean fun overtimeSource(sql: DSLContext): OvertimeDataSource = PostgresOvertimeDataSource(sql)

    @Bean
    fun overtime(source: OvertimeDataSource, json: ObjectMapper): OvertimeRepository =
        StoredOvertimeRepository(source, json)

    @Bean
    fun planOvertime(
        overtime: OvertimeRepository,
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        schedules: ScheduleRepository,
    ) =
        PlanOvertimeRequest(
            overtime,
            periods,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            schedules,
        )

    @Bean
    fun submitOvertimeActual(
        overtime: OvertimeRepository,
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        approvals: ApprovalRepository,
    ) =
        SubmitOvertimeActual(
            overtime,
            periods,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            approvals,
        )

    @Bean
    fun withdrawOvertime(
        overtime: OvertimeRepository,
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        approvals: ApprovalRepository,
    ) =
        WithdrawOvertimeRequest(
            overtime,
            periods,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            approvals,
        )

    @Bean
    fun decideOvertime(
        overtime: OvertimeRepository,
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        approvals: ApprovalRepository,
    ) =
        DecideOvertimeRequest(
            overtime,
            periods,
            people,
            companies,
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            approvals,
        )

    @Bean
    fun getOvertime(
        overtime: OvertimeRepository,
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        approvals: ApprovalRepository,
    ) =
        GetOvertimeRequest(
            overtime,
            periods,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            approvals,
        )

    @Bean
    fun listOvertime(
        overtime: OvertimeRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ListOvertimeRequests(overtime, people, companies, members, identities, transactions, clock)
}
