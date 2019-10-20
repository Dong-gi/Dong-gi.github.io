import { Component, OnInit } from '@angular/core';
import { I18nService } from '../i18n.service';

@Component({
  selector: 'app-lang-selector',
  templateUrl: './lang-selector.component.html',
  styleUrls: ['./lang-selector.component.css']
})
export class LangSelectorComponent implements OnInit {
  lang = ''

  constructor(private i18nService: I18nService) { }

  ngOnInit() {
  }

  setLang(lang: string) {
    this.i18nService.setLang(lang);
    this.lang = this.i18nService.getLang();
  }
}
