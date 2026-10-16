package dev.fajar.hris.workforce.data.di

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
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
    @Bean
    fun ownedWorkSchedule(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetOwnedWorkSchedule(
            schedules,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun ownedRosterDay(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetOwnedRosterDay(
            schedules,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun ownedWorkHoliday(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetOwnedWorkHoliday(
            schedules,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun overtimePlanningEmployees(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListOvertimePlanningEmployees(
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun overtimePlanningReview(
        schedules: ScheduleRepository,
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetOvertimePlanningReview(
            schedules,
            periods,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun attendanceExceptions(
        attendance: AttendanceRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListAttendanceExceptions(
            attendance,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun workforceEmployees(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListWorkforceEmployees(
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun employeeCalendarReview(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetEmployeeCalendarReview(
            schedules,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

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
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        SaveShift(
            schedules,
            operations,
            journal,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listShifts(
        schedules: ScheduleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListShifts(schedules, companies, members, identities, transactions, security, clock)

    @Bean
    fun getShift(
        schedules: ScheduleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetShift(schedules, companies, members, identities, transactions, security, clock)

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
        security: IdentitySecurityPolicy,
        clock: Clock,
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
            security,
            clock,
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
        security: IdentitySecurityPolicy,
        clock: Clock,
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
            security,
            clock,
        )

    @Bean
    fun resetRosterDay(
        periods: WorkPeriodRepository,
        schedules: ScheduleRepository,
        people: PeopleRepository,
        operations: OperationRepository,
        journal: ChangeJournalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ResetRosterDay(
            periods,
            schedules,
            people,
            operations,
            journal,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun selfWorkforceEmployments(
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListSelfWorkforceEmployments(
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun employeeCalendar(
        schedules: ScheduleRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetEmployeeCalendar(
            schedules,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

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
        security: IdentitySecurityPolicy,
        clock: Clock,
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
            security,
            clock,
        )

    @Bean
    fun listHolidays(
        schedules: ScheduleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListWorkHolidays(schedules, companies, members, identities, transactions, security, clock)

    @Bean
    fun getHoliday(
        schedules: ScheduleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = GetWorkHoliday(schedules, companies, members, identities, transactions, security, clock)

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
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        IssueAttendanceCaptureWindow(
            attendance,
            people,
            companies,
            operations,
            members,
            identities,
            transactions,
            security,
            clock,
        )

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
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
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
            members,
            identities,
            transactions,
            security,
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
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ReviewAttendance(
            periods,
            attendance,
            corrections,
            people,
            operations,
            journal,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun attendanceDay(
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetAttendanceDay(
            attendance,
            corrections,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun employeeAttendance(
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetEmployeeAttendance(
            attendance,
            corrections,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

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
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
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
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun correctionHistory(
        corrections: AttendanceCorrectionRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetAttendanceCorrectionHistory(
            corrections,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

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
        security: IdentitySecurityPolicy,
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
            security,
        )

    @Bean
    fun advanceWorkPeriodClose(
        periods: WorkPeriodRepository,
        jobs: JobRepository,
        schedules: ScheduleRepository,
        attendance: AttendanceRepository,
        corrections: AttendanceCorrectionRepository,
        journal: ChangeJournalRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
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
            companies,
            members,
            identities,
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
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        RecoverWorkPeriod(
            periods,
            jobs,
            operations,
            journal,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listWorkPeriods(
        periods: WorkPeriodRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = ListWorkPeriods(periods, companies, members, identities, transactions, security, clock)

    @Bean
    fun getWorkPeriodOverview(
        periods: WorkPeriodRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        GetWorkPeriodOverview(
            periods,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun listWorkPeriodEmployees(
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) =
        ListWorkPeriodEmployees(
            periods,
            people,
            companies,
            members,
            identities,
            transactions,
            security,
            clock,
        )

    @Bean
    fun getWorkPeriodSnapshot(
        periods: WorkPeriodRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: IdentitySecurityPolicy,
    ) =
        GetWorkPeriodSnapshot(
            periods,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )

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
        security: IdentitySecurityPolicy,
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
            security,
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
        security: IdentitySecurityPolicy,
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
            security,
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
        security: IdentitySecurityPolicy,
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
            security,
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
        security: IdentitySecurityPolicy,
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
            security,
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
        security: IdentitySecurityPolicy,
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
            security,
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
        security: IdentitySecurityPolicy,
    ) =
        ListOvertimeRequests(
            overtime,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
            security,
        )
}
