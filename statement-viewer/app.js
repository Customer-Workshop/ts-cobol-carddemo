(function () {
  "use strict";

  var fileInput = document.getElementById("statement-file");
  var dropZone = document.getElementById("drop-zone");
  var statement = document.getElementById("statement");
  var errorPanel = document.getElementById("error-panel");
  var errorList = document.getElementById("error-list");
  var accountPicker = document.getElementById("account-picker");
  var accountSelect = document.getElementById("account-select");
  var records = [];

  var shape = {
    accountId: "string",
    customer: {
      customerId: "string", firstName: "string", middleName: "string", lastName: "string",
      addressLine1: "string", addressLine2: "string", addressLine3: "string",
      stateCode: "string", countryCode: "string", zip: "string", phone1: "string", phone2: "string", ficoScore: "integer"
    },
    account: {
      activeStatus: "string", currentBalance: "number", creditLimit: "number", cashCreditLimit: "number",
      openDate: "string", expirationDate: "string", reissueDate: "string",
      currentCycleCredit: "number", currentCycleDebit: "number", groupId: "string"
    },
    cards: [{ cardNumber: "string" }],
    transactions: [{
      transactionId: "string", cardNumber: "string", typeCode: "string", categoryCode: "string", source: "string",
      description: "string", amount: "number", merchantId: "string", merchantName: "string", merchantCity: "string",
      merchantZip: "string", originTimestamp: "string", processTimestamp: "string"
    }]
  };
  var money = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });

  function element(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function child(parent, tag, className, text) {
    var node = element(tag, className, text);
    parent.appendChild(node);
    return node;
  }
  function showErrors(errors) {
    records = [];
    accountPicker.classList.add("is-hidden");
    statement.classList.add("is-hidden");
    errorList.replaceChildren();
    errors.forEach(function (error) { child(errorList, "li", "", error); });
    errorPanel.classList.remove("is-hidden");
  }
  function validate(value, expected, path, errors) {
    if (expected instanceof Array) {
      if (!Array.isArray(value)) { errors.push(path + " must be an array."); return; }
      value.forEach(function (item, index) { validate(item, expected[0], path + "[" + index + "]", errors); });
      return;
    }
    if (!value || typeof value !== "object" || Array.isArray(value)) { errors.push(path + " must be an object."); return; }
    Object.keys(expected).forEach(function (key) {
      var currentPath = path ? path + "." + key : key;
      if (!Object.prototype.hasOwnProperty.call(value, key)) { errors.push(currentPath + " is required."); return; }
      var wanted = expected[key];
      if (typeof wanted === "string") {
        var actual = typeof value[key];
        if (wanted === "integer" ? (!Number.isInteger(value[key])) : (actual !== wanted || (wanted === "number" && !Number.isFinite(value[key])))) {
          errors.push(currentPath + " must be a " + (wanted === "integer" ? "integer" : wanted) + ".");
        }
      } else validate(value[key], wanted, currentPath, errors);
    });
    Object.keys(value).forEach(function (key) {
      if (!Object.prototype.hasOwnProperty.call(expected, key)) errors.push(path + "." + key + " is not allowed.");
    });
  }
  function parseText(text) {
    try { return [JSON.parse(text)]; } catch (wholeError) {
      var lines = text.split(/\r?\n/), parsed = [], errors = [];
      lines.forEach(function (line, index) {
        if (!line.trim()) return;
        try { parsed.push(JSON.parse(line)); } catch (lineError) { errors.push("Line " + (index + 1) + " is not valid JSON."); }
      });
      if (errors.length) throw new Error(errors.join(" "));
      if (!parsed.length) throw new Error("The file is empty.");
      return parsed;
    }
  }
  function readFile(file) {
    try {
      if (!file) return;
      var reader = new FileReader();
      reader.onload = function () {
        try { loadText(reader.result); } catch (error) { showErrors([error.message || "Unable to read the statement."]); }
      };
      reader.onerror = function () { showErrors(["The selected file could not be read."]); };
      reader.readAsText(file);
    } catch (error) { showErrors([error.message || "Unable to read the statement."]); }
  }
  function loadText(text) {
    var parsed = parseText(text);
    var errors = [];
    parsed.forEach(function (record, index) { validate(record, shape, "record " + (index + 1), errors); });
    if (errors.length) { showErrors(errors); return; }
    records = parsed;
    errorPanel.classList.add("is-hidden");
    accountSelect.replaceChildren();
    records.forEach(function (record, index) {
      var option = element("option", "", record.accountId + " · " + record.customer.lastName);
      option.value = String(index);
      accountSelect.appendChild(option);
    });
    accountPicker.classList.toggle("is-hidden", records.length < 2);
    render(records[0]);
  }
  function maskedCard(number) {
    var digits = String(number).replace(/\s/g, "");
    return "•••• •••• •••• " + digits.slice(-4);
  }
  function displayDate(value) {
    if (typeof value !== "string") return "—";
    var match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (!match) return value;
    return match[2] + "/" + match[3] + "/" + match[1];
  }
  function displayMoney(value) { return money.format(Math.abs(value)) === "$0.00" || value >= 0 ? money.format(value) : "-$" + money.format(Math.abs(value)).replace("$", ""); }
  function moneyNode(value) { return element("span", "amount" + (value < 0 ? " negative" : ""), displayMoney(value)); }
  function addSummary(parent, label, value, isMoney) {
    var card = child(parent, "div", "summary-card");
    child(card, "div", "summary-label", label);
    child(card, "div", "summary-value", isMoney ? displayMoney(value) : String(value || "—"));
  }
  function render(record) {
    statement.classList.remove("is-hidden");
    statement.replaceChildren();
    var header = child(statement, "header", "statement-header");
    child(header, "div", "statement-kicker", "CardDemo Bank");
    child(header, "h2", "", "Account Statement");
    var meta = child(header, "div", "header-meta");
    child(meta, "span", "", "Account " + record.accountId);
    child(meta, "span", "", "Customer " + record.customer.customerId);
    var body = child(statement, "div", "statement-body");
    var customerRow = child(body, "div", "customer-row");
    var identity = child(customerRow, "div");
    var name = [record.customer.firstName, record.customer.middleName, record.customer.lastName].filter(Boolean).join(" ");
    child(identity, "p", "customer-name", name);
    var address = [record.customer.addressLine1, record.customer.addressLine2, record.customer.addressLine3,
      [record.customer.stateCode, record.customer.zip].filter(Boolean).join(" "),
      record.customer.countryCode, record.customer.phone1, record.customer.phone2].filter(Boolean).join("\n");
    child(identity, "div", "address", address);
    var summary = child(body, "section");
    var title = child(summary, "div", "section-title"); child(title, "h3", "", "Account summary"); child(title, "span", "", "FICO " + record.customer.ficoScore);
    var grid = child(summary, "div", "summary-grid"), a = record.account;
    addSummary(grid, "Current balance", a.currentBalance, true); addSummary(grid, "Credit limit", a.creditLimit, true);
    addSummary(grid, "Cash credit limit", a.cashCreditLimit, true); addSummary(grid, "Cycle credit", a.currentCycleCredit, true);
    addSummary(grid, "Cycle debit", a.currentCycleDebit, true); addSummary(grid, "Open date", displayDate(a.openDate));
    addSummary(grid, "Expiration", displayDate(a.expirationDate)); addSummary(grid, "Reissue date", displayDate(a.reissueDate));
    addSummary(grid, "Active status", a.activeStatus); addSummary(grid, "Group ID", a.groupId);
    var cardsSection = child(body, "section"), cardTitle = child(cardsSection, "div", "section-title");
    child(cardTitle, "h3", "", "Cards"); child(cardTitle, "span", "", record.cards.length + " linked");
    var cards = child(cardsSection, "div", "cards-list");
    record.cards.forEach(function (card) { child(cards, "div", "card-chip", maskedCard(card.cardNumber)); });
    var txSection = child(body, "section"), txTitle = child(txSection, "div", "section-title");
    child(txTitle, "h3", "", "Transactions"); child(txTitle, "span", "", record.transactions.length + " this cycle");
    var wrap = child(txSection, "div", "table-wrap"), table = child(wrap, "table"), thead = child(table, "thead"), headRow = child(thead, "tr");
    ["Date / time", "Description", "Merchant", "Type / category", "Card", "Amount"].forEach(function (label) { child(headRow, "th", "", label); });
    var tbody = child(table, "tbody"), total = 0;
    if (!record.transactions.length) { var empty = child(tbody, "tr"); var emptyCell = child(empty, "td", "empty-state", "No transactions to display for this cycle."); emptyCell.colSpan = 6; }
    record.transactions.forEach(function (tx) {
      total += tx.amount; var row = child(tbody, "tr");
      child(row, "td", "date-cell", tx.originTimestamp || tx.processTimestamp || "—");
      child(row, "td", "", tx.description);
      var merchant = child(row, "td"); child(merchant, "div", "merchant", tx.merchantName); child(merchant, "div", "merchant-city", tx.merchantCity);
      child(row, "td", "", tx.typeCode + " / " + tx.categoryCode); child(row, "td", "card-chip", maskedCard(tx.cardNumber));
      var amountCell = child(row, "td", "amount"); amountCell.appendChild(moneyNode(tx.amount));
    });
    var foot = child(table, "tfoot"), footRow = child(foot, "tr"); child(footRow, "td", "", ""); child(footRow, "td", "", ""); child(footRow, "td", "", ""); child(footRow, "td", "", ""); child(footRow, "td", "", "Total");
    var totalCell = child(footRow, "td", "amount"); totalCell.appendChild(moneyNode(total));
  }
  fileInput.addEventListener("change", function () { try { readFile(fileInput.files[0]); } catch (error) { showErrors([error.message]); } });
  accountSelect.addEventListener("change", function () { try { render(records[Number(accountSelect.value)]); } catch (error) { showErrors([error.message]); } });
  ["dragenter", "dragover"].forEach(function (name) { dropZone.addEventListener(name, function (event) { try { event.preventDefault(); dropZone.classList.add("is-dragging"); } catch (error) { showErrors([error.message]); } }); });
  ["dragleave", "drop"].forEach(function (name) { dropZone.addEventListener(name, function (event) { try { event.preventDefault(); dropZone.classList.remove("is-dragging"); if (name === "drop") readFile(event.dataTransfer.files[0]); } catch (error) { showErrors([error.message]); } }); });
}());
