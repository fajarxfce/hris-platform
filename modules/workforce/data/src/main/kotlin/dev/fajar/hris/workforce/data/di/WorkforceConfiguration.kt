package dev.fajar.hris.workforce.data.di

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.data.datasources.*
import dev.fajar.hris.workforce.data.repositories.StoredScheduleRepository
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
}
