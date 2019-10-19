import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-lang-selector',
  templateUrl: './lang-selector.component.html',
  styleUrls: ['./lang-selector.component.css']
})
export class LangSelectorComponent implements OnInit {
  lang = ''

  constructor() { }

  ngOnInit() {
  }

  setLang(lang: string) {
    this.lang = lang;
  }
}
