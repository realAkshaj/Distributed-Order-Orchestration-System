import { useState } from 'react';
import { api } from './api';
import { OrderForm } from './components/OrderForm';
import { SagaDetail } from './components/SagaDetail';
import { SagaTable } from './components/SagaTable';
import { StatsBar } from './components/StatsBar';
import { usePolling } from './usePolling';

export default function App() {
  const { data: sagas, error, refresh } = usePolling(api.sagas, 1500);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = sagas?.find((s) => s.id === selectedId) ?? null;

  const handlePlaced = (sagaId: string) => {
    setSelectedId(sagaId);
    refresh();
  };

  return (
    <main>
      <header>
        <h1>Order Orchestration</h1>
        <p className="muted">Saga orchestrator · Kafka · Spring Boot microservices</p>
      </header>

      <StatsBar />

      <div className="layout">
        <aside>
          <OrderForm onPlaced={handlePlaced} />
          {selected && <SagaDetail saga={selected} />}
        </aside>
        <section>
          {error && <p className="error">Cannot reach orchestrator: {error}</p>}
          <SagaTable sagas={sagas ?? []} selectedId={selectedId} onSelect={setSelectedId} />
        </section>
      </div>
    </main>
  );
}
