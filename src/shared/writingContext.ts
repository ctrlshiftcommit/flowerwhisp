export const WRITING_PURPOSES = ['personal', 'work', 'email', 'other'] as const

export type WritingPurpose = (typeof WRITING_PURPOSES)[number]

export type ApplicationContextSource = 'rule' | 'classifier' | 'fallback'

/** Safe application metadata that may be included in a cleanup prompt. */
export interface WritingApplicationContext {
  readonly applicationName: string
  readonly purpose: WritingPurpose
  readonly source: ApplicationContextSource
}
