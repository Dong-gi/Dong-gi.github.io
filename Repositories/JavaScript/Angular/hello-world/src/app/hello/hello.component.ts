import { Component, OnInit } from '@angular/core';
import { I18nService } from '../i18n.service';

@Component({
  selector: 'app-hello',
  templateUrl: './hello.component.html',
  styleUrls: ['./hello.component.css']
})
export class HelloComponent implements OnInit {
  message = 'Hello';
  name = 'World';

  constructor(private i18nService: I18nService) { }

  ngOnInit() {
  }

  setName(name: string) {
    this.name = name;
    this.message = (this.i18nService.getLang() === 'KOREAN'? '환영합니다 ' : 'Hello') + name;
  }

}
