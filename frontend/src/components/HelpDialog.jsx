import { Check } from "lucide-react";
import Modal from "./Modal.jsx";

export default function HelpDialog({ onClose }) {
  return (
    <Modal title="Jūsų ShareNote erdvė" onClose={onClose}>
      <div className="help-content">
        <p>
          Organizacijos jungia grupes. Grupės gali veikti ir savarankiškai.
          Kiekvienas užrašas priklauso vienai grupei.
        </p>
        <h3>Kaip pradėti?</h3>
        <ol>
          <li>Pasirinkite grupę arba raskite užrašą per paiešką.</li>
          <li>Spustelėkite įrašą, kad perskaitytumėte visą turinį.</li>
          <li>Administratoriai gali kurti, redaguoti ir trinti įrašus.</li>
        </ol>
        <h3>Prieigos teisės</h3>
        <p>
          Užsiregistravę naudotojai gali peržiūrėti visą biblioteką.
          Administratoriaus paskyrą parengia sistemos prižiūrėtojas.
        </p>
        <p>
          Norėdami ištrinti grupę, pirmiausia ištrinkite jos užrašus. Prieš
          trindami organizaciją pašalinkite jos grupes.
        </p>
      </div>
      <button className="primary" onClick={onClose}>
        Supratau
        <Check size={17} />
      </button>
    </Modal>
  );
}
