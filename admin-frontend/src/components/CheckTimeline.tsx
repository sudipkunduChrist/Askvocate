import type { PendingDocument } from '../api/types';
import {
  hasMismatch,
  isAwaitingAadhaar,
  mismatchFields,
} from '../utils/documents';
import { fieldLabel, pluralize } from '../utils/format';
import { Icon, type IconName } from './Icon';

type StepState = 'done' | 'attention' | 'waiting';

interface Step {
  title: string;
  detail: string;
  state: StepState;
  icon: IconName;
}

const STATE_LABEL: Record<StepState, string> = {
  done: 'Passed',
  attention: 'Needs attention',
  waiting: 'Waiting',
};

export function CheckTimeline({ document }: { document: PendingDocument }) {
  const extractedCount = Object.keys(document.extractedData ?? {}).length;
  const qrAvailable = !isAwaitingAadhaar(document);
  const mismatches = mismatchFields(document);
  const mismatch = hasMismatch(document);

  const steps: Step[] = [
    {
      title: 'PAN OCR',
      detail:
        extractedCount > 0
          ? `${extractedCount} fields read from the uploaded card.`
          : 'Waiting for readable details from the uploaded card.',
      state: extractedCount > 0 ? 'done' : 'waiting',
      icon: 'document',
    },
    {
      title: 'Aadhaar QR',
      detail: qrAvailable
        ? 'Signed Aadhaar details are available for comparison.'
        : 'No signed Aadhaar QR is linked to this applicant.',
      state: qrAvailable ? 'done' : 'waiting',
      icon: 'shield',
    },
    {
      title: 'Cross-document match',
      detail: mismatch
        ? `${mismatches.length} ${pluralize(
            mismatches.length,
            'field needs',
            'fields need',
          )} attention: ${mismatches.map(fieldLabel).join(', ')}.`
        : !qrAvailable || extractedCount === 0
          ? 'Comparison begins once both records are available.'
          : 'PAN and Aadhaar details agree.',
      state: mismatch ? 'attention' : qrAvailable ? 'done' : 'waiting',
      icon: 'check',
    },
  ];

  return (
    <ol className="timeline">
      {steps.map((step) => (
        <li
          className={`timeline__item timeline__item--${step.state}`}
          key={step.title}
        >
          <span className="timeline__dot" aria-hidden="true">
            <Icon name={step.icon} size={10} strokeWidth={2.2} />
          </span>
          <div className="timeline__content">
            <div className="timeline__heading">
              <h4>{step.title}</h4>
              <span className="timeline__state">{STATE_LABEL[step.state]}</span>
            </div>
            <p>{step.detail}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}
