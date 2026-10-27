import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react'
import { useLanguage } from '../../contexts/LanguageContext'
import { FieldError } from './FormField'
import {
  MONTHS_EN,
  MONTHS_TH,
  WEEKDAYS_EN,
  WEEKDAYS_TH,
  buildMonthCells,
  parseIso,
  startOfDay,
  toIso,
  type MenuMode,
} from './dateUtils'
import '../../styles/date-picker.css'

type DatePickerProps = {
  label: ReactNode
  required?: boolean
  name?: string
  field?: string
  value: string
  onChange: (value: string) => void
  onBlur?: () => void
  error?: string
  placeholder?: string
  min?: string
  max?: string
  className?: string
}

export default function DatePicker({
  label,
  required,
  name,
  field,
  value,
  onChange,
  onBlur,
  error,
  placeholder,
  min,
  max,
  className,
}: DatePickerProps) {
  const { locale, localeTag: tag } = useLanguage()
  const isThai = locale === 'th'
  const containerRef = useRef<HTMLDivElement>(null)
  const yearListRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [menu, setMenu] = useState<MenuMode>('none')
  const selected = parseIso(value)
  const minDate = min ? parseIso(min) : null
  const maxDate = max ? parseIso(max) : null
  const [view, setView] = useState(() => selected || maxDate || new Date())

  useEffect(() => {
    if (!open) {
      setMenu('none')
      return
    }
    setView(selected || maxDate || new Date())
  }, [open])

  useEffect(() => {
    if (!open) return
    const onPointer = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
        onBlur?.()
      }
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (menu !== 'none') setMenu('none')
        else {
          setOpen(false)
          onBlur?.()
        }
      }
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open, onBlur, menu])

  useEffect(() => {
    if (menu !== 'year' || !yearListRef.current) return
    const active = yearListRef.current.querySelector<HTMLElement>('[data-active="true"]')
    active?.scrollIntoView({ block: 'center' })
  }, [menu, view])

  const displayValue = useMemo(() => {
    if (!selected) return ''
    return selected.toLocaleDateString(tag, { day: 'numeric', month: 'long', year: 'numeric' })
  }, [selected, tag])

  const weekdays = isThai ? WEEKDAYS_TH : WEEKDAYS_EN
  const months = isThai ? MONTHS_TH : MONTHS_EN
  const yearLabel = (year: number) => (isThai ? year + 543 : year)

  const yearOptions = useMemo(() => {
    const end = maxDate?.getFullYear() ?? new Date().getFullYear()
    const start = minDate?.getFullYear() ?? end - 100
    const years: number[] = []
    for (let y = end; y >= start; y -= 1) years.push(y)
    return years
  }, [minDate, maxDate])

  const cells = useMemo(() => buildMonthCells(view), [view])

  const isDisabled = (date: Date) => {
    const day = startOfDay(date).getTime()
    if (minDate && day < startOfDay(minDate).getTime()) return true
    if (maxDate && day > startOfDay(maxDate).getTime()) return true
    return false
  }

  const shiftMonth = (delta: number) => {
    setMenu('none')
    setView((current) => new Date(current.getFullYear(), current.getMonth() + delta, 1))
  }

  const pick = (date: Date) => {
    if (isDisabled(date)) return
    onChange(toIso(date))
    setOpen(false)
    setMenu('none')
    onBlur?.()
  }

  const today = startOfDay(new Date())
  const defaultPlaceholder = isThai ? 'เลือกวันที่' : locale === 'zh' ? '选择日期' : 'Select date'
  const viewMonth = view.getMonth()
  const viewYear = view.getFullYear()

  return (
    <div
      className={['date-picker', className].filter(Boolean).join(' ')}
      ref={containerRef}
      data-field={field || name}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) onBlur?.()
      }}
    >
      <span className="date-picker-label">
        {label} {required ? <em>*</em> : null}
      </span>
      <button
        type="button"
        name={name}
        className={`date-picker-trigger${open ? ' open' : ''}${error ? ' invalid' : ''}${value ? '' : ' empty'}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-invalid={Boolean(error)}
        onClick={() => setOpen((prev) => !prev)}
      >
        <span>{displayValue || placeholder || defaultPlaceholder}</span>
        <CalendarDays aria-hidden="true" />
      </button>
      {open ? (
        <div className="date-picker-panel" role="dialog" aria-label={typeof label === 'string' ? label : undefined}>
          <div className="date-picker-toolbar">
            <button type="button" className="date-picker-nav" aria-label="Previous month" onClick={() => shiftMonth(-1)}>
              <ChevronLeft />
            </button>
            <div className="date-picker-heading">
              <button
                type="button"
                className={`date-picker-chip${menu === 'month' ? ' active' : ''}`}
                aria-expanded={menu === 'month'}
                onClick={() => setMenu((current) => (current === 'month' ? 'none' : 'month'))}
              >
                <span>{months[viewMonth]}</span>
                <ChevronDown />
              </button>
              <button
                type="button"
                className={`date-picker-chip year${menu === 'year' ? ' active' : ''}`}
                aria-expanded={menu === 'year'}
                onClick={() => setMenu((current) => (current === 'year' ? 'none' : 'year'))}
              >
                <span>{yearLabel(viewYear)}</span>
                <ChevronDown />
              </button>
            </div>
            <button type="button" className="date-picker-nav" aria-label="Next month" onClick={() => shiftMonth(1)}>
              <ChevronRight />
            </button>
          </div>

          {menu === 'month' ? (
            <div className="date-picker-menu months" role="listbox" aria-label="Month">
              {months.map((month, index) => (
                <button
                  key={month}
                  type="button"
                  role="option"
                  aria-selected={index === viewMonth}
                  className={index === viewMonth ? 'active' : ''}
                  onClick={() => {
                    setView(new Date(viewYear, index, 1))
                    setMenu('none')
                  }}
                >
                  {month}
                </button>
              ))}
            </div>
          ) : null}

          {menu === 'year' ? (
            <div className="date-picker-menu years" role="listbox" aria-label="Year" ref={yearListRef}>
              {yearOptions.map((year) => (
                <button
                  key={year}
                  type="button"
                  role="option"
                  data-active={year === viewYear ? 'true' : undefined}
                  aria-selected={year === viewYear}
                  className={year === viewYear ? 'active' : ''}
                  onClick={() => {
                    setView(new Date(year, viewMonth, 1))
                    setMenu('none')
                  }}
                >
                  {yearLabel(year)}
                </button>
              ))}
            </div>
          ) : null}

          {menu === 'none' ? (
            <>
              <div className="date-picker-weekdays">
                {weekdays.map((day) => <span key={day}>{day}</span>)}
              </div>
              <div className="date-picker-grid">
                {cells.map(({ date, inMonth }) => {
                  const iso = toIso(date)
                  const disabled = isDisabled(date)
                  const isSelected = selected ? toIso(selected) === iso : false
                  const isToday = toIso(today) === iso
                  return (
                    <button
                      key={`${iso}-${inMonth ? 'in' : 'out'}`}
                      type="button"
                      disabled={disabled}
                      className={[
                        'date-picker-day',
                        inMonth ? '' : 'muted',
                        isSelected ? 'selected' : '',
                        isToday ? 'today' : '',
                      ].filter(Boolean).join(' ')}
                      onClick={() => pick(date)}
                    >
                      {date.getDate()}
                    </button>
                  )
                })}
              </div>
            </>
          ) : null}

          <div className="date-picker-footer">
            <button
              type="button"
              className="date-picker-clear"
              onClick={() => {
                onChange('')
                setOpen(false)
                setMenu('none')
                onBlur?.()
              }}
            >
              {isThai ? 'ล้าง' : 'Clear'}
            </button>
            <button
              type="button"
              className="date-picker-today"
              disabled={isDisabled(today)}
              onClick={() => pick(today)}
            >
              {isThai ? 'วันนี้' : 'Today'}
            </button>
          </div>
        </div>
      ) : null}
      <FieldError text={error} />
    </div>
  )
}
