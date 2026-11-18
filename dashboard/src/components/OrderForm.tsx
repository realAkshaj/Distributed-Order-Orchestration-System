import { FormEvent, useState } from 'react';
import { api } from '../api';
import { PRODUCTS, randomUuid } from '../format';

interface Props {
  onPlaced: (sagaId: string) => void;
}

export function OrderForm({ onPlaced }: Props) {
  const [productId, setProductId] = useState(PRODUCTS[0].id);
  const [quantity, setQuantity] = useState(1);
  const [forceFailure, setForceFailure] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const price = PRODUCTS.find((p) => p.id === productId)!.price;

  const place = async (pid: string, qty: number, force: boolean) => {
    const unit = PRODUCTS.find((p) => p.id === pid)!.price;
    const { sagaId } = await api.placeOrder(
      { customerId: randomUuid(), productId: pid, quantity: qty, totalAmount: unit * qty },
      force,
    );
    return sagaId;
  };

  const run = async (action: () => Promise<string>) => {
    setBusy(true);
    setError(null);
    try {
      onPlaced(await action());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to place order');
    } finally {
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    run(() => place(productId, quantity, forceFailure));
  };

  const burst = () =>
    run(async () => {
      let last = '';
      for (let i = 0; i < 20; i++) {
        const product = PRODUCTS[Math.floor(Math.random() * 2)];
        last = await place(product.id, 1 + Math.floor(Math.random() * 3), false);
      }
      return last;
    });

  return (
    <form className="card order-form" onSubmit={submit}>
      <h2>Place order</h2>
      <label>
        Product
        <select value={productId} onChange={(e) => setProductId(e.target.value)}>
          {PRODUCTS.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        Quantity
        <input type="number" min={1} max={100} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
      </label>
      <label className="check">
        <input type="checkbox" checked={forceFailure} onChange={(e) => setForceFailure(e.target.checked)} />
        Force payment failure
      </label>
      <div className="total">Total {(price * quantity).toLocaleString(undefined, { style: 'currency', currency: 'USD' })}</div>
      <div className="row">
        <button type="submit" disabled={busy}>Place order</button>
        <button type="button" className="secondary" disabled={busy} onClick={burst}>Send 20 orders</button>
      </div>
      {error && <p className="error">{error}</p>}
    </form>
  );
}
