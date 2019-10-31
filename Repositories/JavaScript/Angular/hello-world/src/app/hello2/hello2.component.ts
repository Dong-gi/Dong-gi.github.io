import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-hello2',
  templateUrl: './hello2.component.html',
  styleUrls: ['./hello2.component.css']
})
export class Hello2Component implements OnInit {
  name = 'World';

  constructor() { }

  ngOnInit() {
    setInterval(() => { this.name = this.name + '!'; }, 1000);
  }

  onChange(name: string) {
    this.name = name;
  }

}
