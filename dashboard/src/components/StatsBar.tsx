import { api } from '../api';
import { IN_FLIGHT } from '../format';
import { usePolling } from '../usePolling';

export function StatsBar() {
  const { data, error } = usePolling(api.stats, 2000);
  if (!data) return <div className="stats muted">{error ? `Orchestrator unreachable (${error})` : 'Loading…'}</div>;

  const c = data.counts;
  const inFlight = IN_FLIGHT.reduce((sum, s) => sum + c[s], 0);
  const finished = c.COMPLETED + c.COMPENSATED + c.FAILED;
  const successRate = finished ? Math.round((c.COMPLETED / finished) * 100) : 0;

  const cards = [
    { label: 'In flight', value: inFlight, tone: 'busy' },
    { label: 'Completed', value: c.COMPLETED, tone: 'ok' },
    { label: 'Compensated', value: c.COMPENSATED, tone: 'warn' },
    { label: 'Failed', value: c.FAILED, tone: 'bad' },
    { label: 'Success rate', value: `${successRate}%`, tone: 'plain' },
    { label: 'Avg completion', value: `${Math.round(data.avgCompletionMillis)} ms`, tone: 'plain' },
  ];

  return (
    <section className="stats">
      {cards.map((card) => (
        <div key={card.label} className={`card stat ${card.tone}`}>
          <span className="stat-value">{card.value}</span>
          <span className="stat-label">{card.label}</span>
        </div>
      ))}
    </section>
  );
}
