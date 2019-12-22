import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class I18nService {
  private lang: string;

  constructor() { }

  setLang(lang: string) {
    console.log(`Language Changed ${this.lang} → ${lang}`);
    this.lang = lang;
  }

  getLang(): string {
    return this.lang;
  }

}
